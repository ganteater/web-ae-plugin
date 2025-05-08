package com.ganteater.ae.processor;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.ganteater.ae.processor.annotation.CommandExamples;
import com.ganteater.ae.processor.annotation.CommandHotHepl;
import com.ganteater.ae.util.xml.easyparser.EasyParser;
import com.ganteater.ae.util.xml.easyparser.Node;

public class DocumentParser extends BaseProcessor {

	public DocumentParser(Processor aParent) {
		super(aParent);
	}

	@CommandExamples({ "<URLParser name='type:property' charset='UTF-8'/>" })
	public void runCommandURLParser(Node action) throws URISyntaxException {
		String name = attr(action, "name");
		String value = (String) getVariableValue(name);
		String charset = (String) attrValue(action, "charset");

		List<NameValuePair> parse = URLEncodedUtils.parse(new URI(value), StringUtils.defaultIfEmpty(charset, "UTF-8"));

		Map<String, String> result = new HashMap<>();
		for (NameValuePair nameValuePair : parse) {
			String key = nameValuePair.getName();
			Object object = result.get(key);
			if (object == null) {
				result.put(key, nameValuePair.getValue());
			}
		}
		setVariableValue(name, result);
	}

	@SuppressWarnings("unchecked")
	@CommandHotHepl("<html></html>")
	@CommandExamples({ "<Extract name='type:property' source='type:property' xpath='type:string' />",
			"<Extract name='type:property' source='type:property' selector='type:string' />" })
	public void runCommandExtract(final Node aCurrentAction)
			throws ParserConfigurationException, SAXException, IOException, XPathExpressionException,
			TransformerFactoryConfigurationError, TransformerException {
		final String theNameAttribut = replaceProperties(aCurrentAction.getAttribute("name"));
		String theNodeAttribut = replaceProperties(aCurrentAction.getAttribute("xpath"));
		final String theSourceAttribut = replaceProperties(aCurrentAction.getAttribute("source"));
		Object source = getVariableValue(theSourceAttribut);
		String theSourceText = null;
		if (source instanceof String) {
			theSourceText = (String) source;
		} else if (source instanceof List && !((List<String>) source).isEmpty()) {
			theSourceText = ((List<String>) source).get(0);
		}

		if (theSourceText == null) {
			debug("Extract from empty xml is ignored.");
			return;
		}

		if (theNodeAttribut != null) {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new InputSource(IOUtils.toInputStream(theSourceText)));
			XPathFactory xPathfactory = XPathFactory.newInstance();
			XPath xpath = xPathfactory.newXPath();
			XPathExpression expr = xpath.compile(theNodeAttribut);
			NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
			List<String> result = new ArrayList();
			for (int i = 0; i < nl.getLength(); i++) {
				org.w3c.dom.Node node = nl.item(i);
				StringWriter writer = new StringWriter();
				Transformer transformer = TransformerFactory.newInstance().newTransformer();
				transformer.transform(new DOMSource(node), new StreamResult(writer));
				result.add(StringUtils.substringAfter(writer.toString(), "?>"));
			}

			setVariableValue(theNameAttribut, result);
		}

		theNodeAttribut = replaceProperties(aCurrentAction.getAttribute("selector"));
		if (theNodeAttribut != null) {
			org.jsoup.nodes.Document doc = Jsoup.parse(theSourceText);
			Elements element = doc.select(theNodeAttribut);
			String text = element.text();
			setVariableValue(theNameAttribut, text);
		}

		theNodeAttribut = replaceProperties(aCurrentAction.getAttribute("node"));
		if (theNodeAttribut != null) {
			final String theAttrAttribut = replaceProperties(aCurrentAction.getAttribute("attribute"));
			final String theNumberTagAttribut = replaceProperties(aCurrentAction.getAttribute("index"));

			if (theSourceText.indexOf("<?") == 0) {
				theSourceText = theSourceText.substring(theSourceText.indexOf("?>") + 2);
			}
			final EasyParser theParser = new EasyParser();
			final Node theNode = theParser.getObject(theSourceText);
			if (theNode == null) {
				return;
			}
			Object theValue = null;
			int theIndex = 0;
			if (theNumberTagAttribut != null) {
				theIndex = Integer.parseInt(theNumberTagAttribut);
			}

			final Node[] nodes = theNode.getNodes(theNodeAttribut);

			if (theNumberTagAttribut != null) {
				if (nodes.length > 0) {
					theValue = extractText(theAttrAttribut, nodes[theIndex], nodes);
				}

			} else {
				final List<String> dataArray = new ArrayList();
				for (int i = 0; i < nodes.length; i++) {
					dataArray.add(extractText(theAttrAttribut, nodes[i], nodes));
				}
				theValue = dataArray;
			}

			setVariableValue(theNameAttribut, theValue);
		}
	}

	private String extractText(final String theAttrAttribute, final Node theNode, final Node[] nodes) {
		String theValue = null;
		if (theAttrAttribute != null) {
			theValue = theNode.getAttribute(theAttrAttribute);
		} else {
			final Node[] nodes2 = theNode.getNodes("$Text");
			if (nodes2.length > 0) {
				final Node node = nodes2[0];
				if (nodes.length > 0) {
					theValue = node.getText();
				}
			} else {
				theValue = theNode.getXMLText();
			}
		}
		return theValue;
	}

	@CommandExamples({ "<PageParser url=''><select name='type:property'>...jsop_select...</select></PageParser>" })
	public void runCommandPageParser(Node action) throws IOException {
		String url = attr(action, "url");
		int timeout = Integer.parseInt(attr(action, "timeout", "2000"));
		Connection connect = Jsoup.connect(url)
				.userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
				.referrer(url).timeout(timeout);

		org.jsoup.nodes.Document doc = connect.get();
		Node[] selects = action.getNodes("select");
		for (Node select : selects) {
			String selectQuery = select.getInnerText();
			String value = doc.select(selectQuery).text();
			setVariableValue(attr(select, "name"), value);
		}
	}
}
