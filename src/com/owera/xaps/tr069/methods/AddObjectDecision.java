package com.owera.xaps.tr069.methods;

import java.sql.SQLException;

import com.owera.common.db.NoAvailableConnectionException;
import com.owera.xaps.base.Log;
import com.owera.xaps.base.UnitJob;
import com.owera.xaps.dbi.UnitJobStatus;
import com.owera.xaps.dbi.util.ProvisioningMode;
import com.owera.xaps.tr069.Properties;
import com.owera.xaps.tr069.HTTPReqResData;
import com.owera.xaps.tr069.SessionData;

public class AddObjectDecision {
	public static void process(HTTPReqResData reqRes) throws SQLException, NoAvailableConnectionException {
		SessionData sessionData = reqRes.getSessionData();
		if (sessionData.getUnit().getProvisioningMode() == ProvisioningMode.REGULAR) {
			if (Properties.isParameterkeyQuirk(sessionData) && sessionData.isProvisioningAllowed()) {
				Log.debug(AddObjectDecision.class, "UnitJob is COMPLETED without verification stage, since CPE does not support AddObject");
				UnitJob uj = new UnitJob(sessionData, sessionData.getJob(), false);
				uj.stop(UnitJobStatus.COMPLETED_OK);
			}
		} 
		reqRes.getResponse().setMethod(TR069Method.EMPTY);
	}

}
