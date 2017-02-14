package com.owera.xaps.tr069.methods;

import com.owera.xaps.base.Log;
import com.owera.xaps.dbi.SyslogConstants;
import com.owera.xaps.dbi.util.SyslogClient;
import com.owera.xaps.tr069.HTTPReqResData;
import com.owera.xaps.tr069.Provisioning;
import com.owera.xaps.tr069.SessionData;
import com.owera.xaps.tr069.exception.TR069Exception;
import com.owera.xaps.tr069.xml.ParameterList;
import com.owera.xaps.tr069.xml.ParameterValueStruct;
import com.owera.xaps.tr069.xml.Parser;

public class AddObjectResponse {

	public static void process(HTTPReqResData reqRes) throws TR069Exception {
		reqRes.getRequest().setMethod(TR069Method.ADD_OBJECT);

		Parser parser = new Parser(reqRes.getRequest().getXml());
		if (parser.getHeader().getNoMoreRequests() != null && parser.getHeader().getNoMoreRequests().getNoMoreRequestFlag())
			reqRes.getSessionData().setNoMoreRequests(true);

		SessionData sessionData = reqRes.getSessionData();

		String objectName = parser.getObjectName();
		String instanceNumber = parser.getInstanceNumber();
		String status = parser.getStatus();

		String user = sessionData.getDbAccess().getXaps().getSyslog().getIdentity().getUser().getUsername();

		Log.notice(HTTPResponseCreator.class, "\t" + objectName + " . " + instanceNumber + " : " + status);
		SyslogClient.notice(sessionData.getUnitId(), "ProvMsg: AddObject to CPE: " + objectName + " . " + instanceNumber + " : " + status, SyslogConstants.FACILITY_TR069, Provisioning.VERSION, user);

	}
}
