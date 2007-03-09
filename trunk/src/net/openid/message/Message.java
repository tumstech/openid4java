/*
 * Copyright 2006-2007 Sxip Identity Corporation
 */

package net.openid.message;

import net.openid.message.ax.AxMessage;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.net.URLEncoder;

import org.apache.log4j.Logger;

/**
 * @author Marius Scurtescu, Johnny Bufu
 */
public class Message
{
    private static Logger _log = Logger.getLogger(Message.class);
    private static final boolean DEBUG = _log.isDebugEnabled();

    // message constants
    public static final String MODE_IDRES = "id_res";
    public static final String MODE_CANCEL = "cancel";
    public static final String MODE_SETUP_NEEDED = "setup_needed";
    public static final String OPENID2_NS = "http://specs.openid.net/auth/2.0";

    private ParameterList _params;
    private int _extCounter;

    // extention type URI -> extension alias : extension present in the message
    private Map _extAliases;

    // extension type URI -> MessageExtensions : extracted extension objects
    private Map _extesion;

    // the URL where this message should be sent, where applicable
    // should remain null for received messages (created from param lists)
    protected String _destinationUrl;

    // type URI -> message extension factory : supported extensions
    private static Map _extensionFactories = new HashMap();

    static
    {
        _extensionFactories.put(AxMessage.OPENID_NS_AX, AxMessage.class);
    }

    protected Message()
    {
        _params = new ParameterList();
        _extCounter = 0;
        _extAliases = new HashMap();
        _extesion   = new HashMap();
    }

    protected Message (ParameterList params)
    {
        this();
        this._params = params;

        //build the extension list when creating a message from a param list
        Iterator iter = _params.getParameters().iterator();
        while (iter.hasNext())
        {
            String key = ((Parameter) iter.next()).getKey();
            if (key.startsWith("openid.ns.") && key.length() > 10)
                _extAliases.put(_params.getParameter(key).getValue(),
                        key.substring(10));
        }
        _extCounter = _extAliases.size();
    }

    public static Message createMessage() throws MessageException
    {
        Message message = new Message();

        if (! message.isValid()) throw new MessageException(
                "Invalid set of parameters for the requested message type");

        if (DEBUG) _log.debug("Created message:\n"
                              + message.keyValueFormEncoding());

        return message;
    }

    public static Message createMessage(ParameterList params)
            throws MessageException
    {
        Message message = new Message(params);

        if (! message.isValid()) throw new MessageException(
                "Invalid set of parameters for the requested message type");

        if (DEBUG) _log.debug("Created message from parameter list:\n"
                              + message.keyValueFormEncoding());

        return message;
    }

    protected Parameter getParameter(String name)
    {
        return _params.getParameter(name);
    }

    public String getParameterValue(String name)
    {
        return _params.getParameterValue(name);
    }

    public boolean hasParameter(String name)
    {
        return _params.hasParameter(name);
    }

    protected void set(String name, String value)
    {
        _params.set(new Parameter(name, value));
    }

    protected List getParameters()
    {
        return _params.getParameters();
    }


    public Map getParameterMap()
    {
        Map params = new LinkedHashMap();

        Iterator iter = _params.getParameters().iterator();
        while (iter.hasNext())
        {
            Parameter p = (Parameter) iter.next();
            params.put( p.getKey(), p.getValue() );
        }

        return params;
    }

    /**
     * Check that all required parameters are present
     */
    public boolean isValid()
    {
        List requiredFields = getRequiredFields();

        Iterator paramIter = _params.getParameters().iterator();
        while (paramIter.hasNext())
        {
            Parameter param = (Parameter) paramIter.next();
            if (!param.isValid())
            {
                _log.warn("Invalid parameter: " + param);
                return false;
            }
        }

        if (requiredFields == null)
            return true;

        Iterator reqIter = requiredFields.iterator();
        while(reqIter.hasNext())
        {
            String required = (String) reqIter.next();
            if (! hasParameter(required))
            {
                _log.warn("Required parameter missing: " + required);
                return false;
            }
        }

        return true;
    }

    public List getRequiredFields()
    {
        return null;
    }

    public String keyValueFormEncoding()
    {
        StringBuffer allParams = new StringBuffer("");

        List parameters = _params.getParameters();
        Iterator iterator = parameters.iterator();
        while (iterator.hasNext())
        {
            Parameter parameter = (Parameter) iterator.next();
            allParams.append(parameter.getKey());
            allParams.append(':');
            allParams.append(parameter.getValue());
            allParams.append('\n');
        }

        return allParams.toString();
    }

    public String wwwFormEncoding()
    {
        StringBuffer allParams = new StringBuffer("");

        List parameters = _params.getParameters();
        Iterator iterator = parameters.iterator();
        while (iterator.hasNext())
        {
            Parameter parameter = (Parameter) iterator.next();

            // All of the keys in the request message MUST be prefixed with "openid."
            if ( ! parameter.getKey().startsWith("openid."))
                allParams.append("openid.");

            try
            {
                allParams.append(URLEncoder.encode(parameter.getKey(), "UTF-8"));
                allParams.append('=');
                allParams.append(URLEncoder.encode(parameter.getValue(), "UTF-8"));
                allParams.append('&');
            }
            catch (UnsupportedEncodingException e)
            {
                return null;
            }
        }

        // remove the trailing '&'
        if (allParams.length() > 0)
            allParams.deleteCharAt(allParams.length() -1);

        return allParams.toString();
    }


    // ------------ extensions implementation ------------
    /**
     * Adds a new extension factory.
     *
     * @param clazz         The implementation class for the extension factory,
     *                      must implement {@link MessageExtensionFactory}.
     */
    public static void addExtensionFactory(Class clazz) throws MessageException
    {
        try
        {
            MessageExtensionFactory extensionFactory =
                    (MessageExtensionFactory) clazz.newInstance();

            if (DEBUG) _log.debug("Adding extension factory for " +
                                  extensionFactory.getTypeUri());

            _extensionFactories.put(extensionFactory.getTypeUri(), clazz);
        }
        catch (Exception e)
        {
            throw new MessageException(
                    "Cannot instantiante message extension factory class: " +
                            clazz.getName());
        }
    }

    /**
     * Returns true if there is an extension factory available for extension
     * identified by the specified Type URI, or false otherwise.
     *
     * @param typeUri   The Type URI that identifies an extension.
     */
    public static boolean hasExtensionFactory(String typeUri)
    {
        return _extensionFactories.containsKey(typeUri);
    }

    /**
     * Gets a MessageExtensionFactory for the specified Type URI
     * if an implementation is available, or null otherwise.
     *
     * @param typeUri   The Type URI that identifies a extension.
     * @see             MessageExtensionFactory Message
     */
    public static MessageExtensionFactory getExtensionFactory(String typeUri)
    {
        if (! hasExtensionFactory(typeUri))
            return null;

        MessageExtensionFactory extensionFactory;

        try
        {
            Class extensionClass = (Class) _extensionFactories.get(typeUri);
            extensionFactory = (MessageExtensionFactory) extensionClass.newInstance();
        }
        catch (Exception e)
        {
            _log.error("Error getting extension factory for " + typeUri);
            return null;
        }

        return extensionFactory;
    }

    /**
     * Retrieves the extension alias that will be used for the extension
     * identified by the supplied extension type URI.
     * <p>
     * If the message contains no parameters for the specified extension,
     * null will be returned.
     *
     * @param extensionTypeUri      The URI that identifies the extension
     * @return                      The extension alias associated with the
     *                              extension specifid by the Type URI
     */
    public String getExtensionAlias(String extensionTypeUri)
    {
        return (String) _extAliases.get(extensionTypeUri);
    }

    /**
     * Gets a set of extension Type URIs that are present in the message.
     */
    public Set getExtensions()
    {
        return _extAliases.keySet();
    }

    /**
     * Gets the URL where the message should be sent, where applicable.
     * Null for received messages.
     *
     * @param   httpGet     If true, the wwwFormEncoding() is appended to the
     *                      destination URL; the return value should be used
     *                      with a GET-redirect.
     *                      If false, the verbatim destination URL is returned,
     *                      which should be used with a FORM POST redirect.
     *
     * @see #wwwFormEncoding()
     */
    public String getDestinationUrl(boolean httpGet)
    {
        if (_destinationUrl == null)
            throw new IllegalStateException("Destination URL not set; " +
                    "is this a received message?");

        if (httpGet)  // append wwwFormEncoding to the destination URL
        {
            boolean hasQuery = _destinationUrl.indexOf("?") > 0;
            String initialChar = hasQuery ? "&" : "?";

            return _destinationUrl + initialChar + wwwFormEncoding();
        }
        else  // should send the keyValueFormEncoding in POST data
            return _destinationUrl;
    }

    /**
     * Adds a set of extension-specific parameters to a message.
     * <p>
     * The parameter names must NOT contain the "openid.<extension_alias>"
     * prefix; it will be generated dynamically, ensuring there are no conflicts
     * between extensions.
     *
     * @param extension             A MessageExtension containing parameters
     *                              to be added to the message
     */
    public void addExtension(MessageExtension extension) throws MessageException
    {
        String typeUri = extension.getTypeUri();

        if (hasExtension(typeUri))
            throw new MessageException("Extension already present: " + typeUri);

        String alias = "ext" + Integer.toString(++ _extCounter);

        _extAliases.put(typeUri, alias);

        if (DEBUG) _log.debug("Adding extension; type URI: "
                              + typeUri + " alias: " +alias);

        set("openid.ns." + alias, typeUri);

        Iterator iter = extension.getParameters().getParameters().iterator();
        while (iter.hasNext())
        {
            Parameter param = (Parameter) iter.next();

            String paramName = param.getKey().length() > 0 ?
                    "openid." + alias + "." + param.getKey() :
                    "openid." + alias;

            set(paramName, param.getValue());
        }
    }

    /**
     * Retrieves the parameters associated with a protocol extension,
     * specified by the given extension type URI.
     * <p>
     * The "openid.ns.<extension_alias>" parameter is NOT included in the
     * returned list. Also, the returned parameter names will have the
     * "openid.<extension_alias>." prefix removed.
     *
     * @param extensionTypeUri      The type URI that identifies the extension
     * @return                      A ParameterList with all parameters
     *                              associated with the specified extension
     */
    private ParameterList getExtensionParams(String extensionTypeUri)
    {
        ParameterList extension = new ParameterList();

        if (hasExtension(extensionTypeUri))
        {
            String extensionAlias = getExtensionAlias(extensionTypeUri);

            Iterator iter = getParameters().iterator();
            while (iter.hasNext())
            {
                Parameter param = (Parameter) iter.next();
                String paramName = null;

                if (param.getKey().startsWith("openid." + extensionAlias + "."))
                    paramName = param.getKey()
                            .substring(8 + extensionAlias.length());

                if (param.getKey().equals("openid." + extensionAlias))
                    paramName = "";

                if (paramName != null)
                    extension.set(new Parameter(paramName, param.getValue()));
            }
        }

        return extension;
    }

    /**
     * Returns true if the message has parameters for the specified
     * extension type URI.
     *
     * @param typeUri       The URI that identifies the extension.
     */
    public boolean hasExtension(String typeUri)
    {
        return _extAliases.containsKey(typeUri);
    }

    /**
     * Gets a MessageExtension for the specified Type URI if an implementation
     * is available, or null otherwise.
     * <p>
     * The returned object will contain the parameters from the message
     * belonging to the specified extension.
     *
     * @param typeUri               The Type URI that identifies a extension.
     */
    public MessageExtension getExtension(String typeUri) throws MessageException
    {
        if (!_extesion.containsKey(typeUri))
        {
            if (hasExtensionFactory(typeUri))
            {
                MessageExtensionFactory extensionFactory = getExtensionFactory(typeUri);

                String mode = getParameterValue("openid.mode");

                MessageExtension extension = extensionFactory.getExtension(
                        getExtensionParams(typeUri), mode.startsWith("checkid_"));

                _extesion.put(typeUri, extension);
            }
            else
                throw new MessageException("Cannot instantiate extension: " + typeUri);
        }

        if (DEBUG) _log.debug("Extracting " + typeUri +" extension from message...");

        return (MessageExtension) _extesion.get(typeUri);
    }
}