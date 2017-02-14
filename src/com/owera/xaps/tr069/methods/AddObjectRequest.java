package com.owera.xaps.tr069.methods;

import java.util.List;

import com.owera.xaps.tr069.Namespace;
import com.owera.xaps.tr069.xml.Body;
import com.owera.xaps.tr069.xml.ParameterValueStruct;

public class AddObjectRequest extends Body {

	private static final String START = "\t\t<cwmp:AddObject>\n";
	private static final String END = "\t\t</cwmp:AddObject>\n";
	private static final String OBJECT_NAME_START = "\t\t\t\t\t<ObjectName>";
	private static final String OBJECT_NAME_END = "</ObjectName>\n";
	private static final String PARAMETER_KEY_START = "\t\t\t<ParameterKey>";
	private static final String PARAMETER_KEY_END = "</ParameterKey>\n";

	private String objectName;
	private String parameterKey;

	public AddObjectRequest(String objectName, String parameterKey) {
		this.objectName = objectName;
		this.parameterKey = parameterKey;
	}

	@Override
	public String toXmlImpl() {
		StringBuilder sb = new StringBuilder(50);
		sb.append(START);
		if (objectName != null)
			sb.append(OBJECT_NAME_START + objectName + OBJECT_NAME_END);
		if (parameterKey != null)
			sb.append(PARAMETER_KEY_START + parameterKey + PARAMETER_KEY_END);
		sb.append(END);
		return sb.toString();
	}
}
