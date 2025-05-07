package com.ganteater.ae.processor;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Base64;

import org.apache.commons.lang.StringEscapeUtils;

import com.ganteater.ae.processor.TaskProcessor;
import com.ganteater.ae.processor.annotation.CommandExamples;
import com.ganteater.ae.util.xml.easyparser.Node;

public class Coder extends TaskProcessor {

	public Coder(TaskProcessor aParent) {
		super(aParent);
	}

	@CommandExamples({ "<Base64Encode name='type:property' source='type:property'/>" })
	public void runCommandBase64Encode(Node action) throws URISyntaxException, UnsupportedEncodingException {
		String name = attr(action, "name");
		Object value = attrValue(action, "source");
		if (value == null) {
			value = attr(action, "value");
		}
		if (value instanceof String) {
			value = Base64.getEncoder().encodeToString(((String) value).getBytes("UTF-8"));
		} else if (value instanceof byte[]) {
			value = Base64.getEncoder().encodeToString((byte[]) value);
		}
		setVariableValue(name, value);
	}

	@CommandExamples({ "<EscapeXml name='type:property' source='type:property'/>" })
	public void runCommandEscapeXml(Node action) throws URISyntaxException {
		String name = attr(action, "name");
		String value = (String) attrValue(action, "source");
		if (value instanceof String) {
			value = StringEscapeUtils.escapeXml(value);
		}
		setVariableValue(name, value);
	}

}
