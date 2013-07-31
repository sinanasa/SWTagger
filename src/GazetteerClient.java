/**
 * Created with IntelliJ IDEA.
 * User: sinanasa
 * Date: 10/3/12
 * Time: 9:34 AM
 * To change this template use File | Settings | File Templates.
 */

/* GazetteerClient.java
 *
 * Example gazetteer protocol client.  Demonstrates how to generate an
 * HTTP POST request from Java and how to parse a gazetteer server
 * response using a JAXP 1.2- and SAX2-compliant validating XML
 * parser.
 *
 * Usage: java GazetteerClient server-url {parse|dump}
 *
 * URLs of running gazetteer servers can be found on the gazetteer
 * protocol test forms page
 * (http://www.alexandria.ucsb.edu/gazetteer/protocol/test-forms.php).
 *
 * This program sends a single 'get-capabilities' request to the
 * server.  The 'parse' option sends the server's response through an
 * XML parser.  The 'dump' option simply writes the response to
 * standard output.
 *
 * This program requires a SAX2-compliant parser which, as of this
 * writing (Java2 1.4.1), is not supplied with the standard Java
 * distribution.  So to run this program you'll have to download and
 * place in your classpath something like Xerces2
 * (http://xml.apache.org/xerces2-j/).
 *
 * Greg Janee <gjanee@alexandria.ucsb.edu>
 * January 2004
 */

import java.io.InputStream;
import java.io.OutputStreamWriter;

import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXParseException;

import org.xml.sax.helpers.DefaultHandler;

public class GazetteerClient extends DefaultHandler {

    private static final String JAXP_SCHEMA_LANGUAGE =
            "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

    private static final String W3C_XML_SCHEMA =
            "http://www.w3.org/2001/XMLSchema";

    private static final String JAXP_SCHEMA_SOURCE =
            "http://java.sun.com/xml/jaxp/properties/schemaSource";

    private static final String ADL_GAZETTEER_PROTOCOL_SCHEMA =
            "http://www.alexandria.ucsb.edu/gazetteer/protocol/" +
                    "gazetteer-service.xsd";

    public static void main (String[] args) throws Exception {

        // Command-line arguments.
        if (args.length != 2 ||
                (!args[1].equals("parse") && !args[1].equals("dump"))) {
            System.err.println("usage: java GazetteerClient server-url {parse|dump}");
            System.exit(1);
        }
        URL u = new URL(args[0]);

        // Set up the connection.
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");

        // Write the request.
        OutputStreamWriter w =
                new OutputStreamWriter(c.getOutputStream(), "UTF-8");
        w.write(
                "<?xml version=\"1.0\"?>" +
                        "<gazetteer-service" +
                        " xmlns=\"http://www.alexandria.ucsb.edu/gazetteer\"" +
                        " version=\"1.2\">" +
                        "<get-capabilities-request/>" +
                        "</gazetteer-service>");
        w.flush();

        // Read the response headers.
        System.out.println("HTTP response code: " + c.getResponseCode());
        System.out.println("HTTP content type: " + c.getContentType());
        System.out.println("HTTP content length: " + c.getContentLength());

        if (args[1].equals("dump")) {

            // Dump option.
            System.out.println("Server response follows...");
            System.out.println();
            InputStream is = c.getInputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) > 0) {
                System.out.write(buf, 0, len);
                System.out.flush();
            }

        } else {

            // Parse option.  First create a parser to read the server
            // response.
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(true);
            f.setValidating(true);
            SAXParser p = f.newSAXParser();
            p.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
            p.setProperty(JAXP_SCHEMA_SOURCE, ADL_GAZETTEER_PROTOCOL_SCHEMA);

            // Now read and parse.  Ideally we should look at the HTTP
            // content type to see if it includes a charset encoding
            // declaration, and if it does, set up an appropriate
            // InputStreamReader.  But for the purposes of this
            // program we ignore that and assume that the encoding is
            // declared in the response itself.
            System.out.println("Outline of server response as reported by " +
                    "parser follows...");
            System.out.println();
            p.parse(c.getInputStream(), new GazetteerClient());

        }

    }

    // Indentation control.

    private int m_indent = 0;

    private void in () {
        m_indent += 2;
    }

    private void out () {
        m_indent -= 2;
    }

    private void indent () {
        for (int i = 0; i < m_indent; ++i) {
            System.out.print(' ');
        }
    }

    // SAX2 parser callbacks.

    private boolean m_lastCallbackWasStartElement = false;

    public void startDocument () {
        System.out.print("start document");
        System.out.flush();
        in();
    }

    public void endDocument () {
        out();
        System.out.println();
        System.out.println("end document");
        System.out.flush();
    }

    public void startElement (String namespaceURI, String localName,
                              String qName, Attributes attributes) {
        System.out.println();
        indent();
        System.out.print("<" + localName + ">");
        System.out.flush();
        in();
        m_lastCallbackWasStartElement = true;
    }

    public void endElement (String namespaceURI, String localName,
                            String qName) {
        out();
        if (m_lastCallbackWasStartElement) {
            System.out.print("...");
        } else {
            System.out.println();
            indent();
        }
        System.out.print("</" + localName + ">");
        System.out.flush();
        m_lastCallbackWasStartElement = false;
    }

    public void warning (SAXParseException e) {
        System.out.println();
        System.out.println();
        System.out.println("warning: " + e.toString());
        System.out.flush();
    }

    public void error (SAXParseException e) {
        System.out.println();
        System.out.println();
        System.out.println("error: " + e.toString());
        System.out.flush();
    }

}