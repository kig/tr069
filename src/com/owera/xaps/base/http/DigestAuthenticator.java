package com.owera.xaps.base.http;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;

import com.owera.common.db.NoAvailableConnectionException;
import com.owera.common.log.Context;
import com.owera.xaps.base.BaseCache;
import com.owera.xaps.base.Log;
import com.owera.xaps.base.NoDataAvailableException;
import com.owera.xaps.dbi.util.SystemParameters;
import com.owera.xaps.tr069.HTTPReqResData;
import com.owera.xaps.tr069.SessionData;
import com.owera.xaps.tr069.exception.TR069AuthenticationException;
import com.owera.xaps.tr069.exception.TR069Exception;

public class DigestAuthenticator {

	/**
	 * MD5 message digest provider.
	 * @throws TR069Exception 
	 */
	//	protected static MessageDigest md5Helper;

	private static void sendChallenge(HttpServletRequest req, HttpServletResponse res) {
		String nonce = DigestUtils.md5Hex(req.getRemoteAddr() + ":" + System.currentTimeMillis() + ":MortenRuler");
		setAuthenticateHeader(res, nonce);
		try {
			res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		} catch (IOException ioe) {
			Log.warn(DigestAuthenticator.class, "Unable to make challenge", ioe);
		}
	}

	public static boolean authenticate(HTTPReqResData reqRes) throws TR069AuthenticationException {

		String authorization = reqRes.getReq().getHeader("authorization");
		if (authorization == null) {
			Log.notice(DigestAuthenticator.class, "Send challenge to CPE, located on IP-address " + reqRes.getReq().getRemoteHost());
			sendChallenge(reqRes.getReq(), reqRes.getRes());
			return false;
		} else {
			return (verify(reqRes, authorization));
		}
	}

	/**
	 * Generates the WWW-Authenticate header.
	 * 
	 * @param request HTTP Servlet request
	 * @param response HTTP Servlet response
	 * @param nonce nonce token
	 */
	private static void setAuthenticateHeader(HttpServletResponse res, String nonce) {

		String realm = Util.getRealm();

		String authenticateHeader = "Digest realm=\"" + realm + "\", " + "qop=\"auth\", nonce=\"" + nonce + "\", " + "opaque=\"" + DigestUtils.md5Hex(nonce) + "\"";
		res.setHeader("WWW-Authenticate", authenticateHeader);
	}

	private static String passwordMd5(String username, String password, String method, String uri, String nonce, String nc, String cnonce, String qop) {
		String realm = Util.getRealm();
		String a1 = username + ":" + realm + ":" + password;
		String md5a1 = DigestUtils.md5Hex(a1);
		String a2 = method + ":" + uri;
		String md5a2 = DigestUtils.md5Hex(a2);
		String a3 = md5a1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + md5a2;
		String md5a3 = DigestUtils.md5Hex(a3);

		return md5a3;
	}

	/**
	 * Verifies login against database
	 * 
	 * @param request HTTP servlet request
	 * @param authorization Authorization credentials from this request
	 * @throws TR069AuthenticationException 
	 */
	private static boolean verify(HTTPReqResData reqRes, String authorization) throws TR069AuthenticationException {

		Log.debug(DigestAuthenticator.class, "Digest verification of CPE starts, located on IP-address " + reqRes.getReq().getRemoteHost());
		authorization = authorization.trim();
		authorization = Util.removePrefix(authorization, "digest");
		authorization = authorization.trim();

		String[] tokens = authorization.split(",(?=(?:[^\"]*\"[^\"]*\")+$)");

		String username = null;
		String realm = null;
		String nonce = null;
		String nc = null;
		String cnonce = null;
		String qop = null;
		String uri = null;
		String response = null;
		String method = reqRes.getReq().getMethod();

		for (int i = 0; i < tokens.length; i++) {
			String currentToken = tokens[i];
			if (currentToken.length() == 0)
				continue;

			int equalSign = currentToken.indexOf('=');
			if (equalSign < 0)
				throw new TR069AuthenticationException("Digest challenge response has incorrect format (CPE IP address: " + reqRes.getReq().getRemoteHost() + ")", null,
						HttpServletResponse.SC_FORBIDDEN);
			String currentTokenName = currentToken.substring(0, equalSign).trim();
			String currentTokenValue = currentToken.substring(equalSign + 1).trim();
			if ("username".equals(currentTokenName))
				username = Util.removeQuotes(currentTokenValue);
			if ("realm".equals(currentTokenName))
				realm = Util.removeQuotes(currentTokenValue, true);
			if ("nonce".equals(currentTokenName))
				nonce = Util.removeQuotes(currentTokenValue);
			if ("nc".equals(currentTokenName))
				nc = Util.removeQuotes(currentTokenValue);
			if ("cnonce".equals(currentTokenName))
				cnonce = Util.removeQuotes(currentTokenValue);
			if ("qop".equals(currentTokenName))
				qop = Util.removeQuotes(currentTokenValue);
			if ("uri".equals(currentTokenName))
				uri = Util.removeQuotes(currentTokenValue);
			if ("response".equals(currentTokenName))
				response = Util.removeQuotes(currentTokenValue);
		}

		if ((username == null) || username.length() < 6 || (realm == null) || (nonce == null) || (uri == null) || (response == null))
			throw new TR069AuthenticationException("Digest challenge response does not contain all necessary parameters (CPE IP address: " + reqRes.getReq().getRemoteHost() + ") (username: "
					+ username + ")", null, HttpServletResponse.SC_FORBIDDEN);

		// Do database read parameters and then perform verification
		String unitId = Util.username2unitId(username);
		Context.put(Context.X, unitId, BaseCache.SESSIONDATA_CACHE_TIMEOUT);
		Log.debug(DigestAuthenticator.class, "Digest verification identifed unit id " + unitId + " from CPE IP-address " + reqRes.getReq().getRemoteHost());
		try {
			SessionData sessionData = reqRes.getSessionData();
			sessionData.setUnitId(unitId);
			sessionData.updateParametersFromDB(unitId);
			//			String secret = sessionData.getOweraParameters().getValue(SystemParameters.SHARED_SECRET);
			BaseCache.putSessionData(unitId, sessionData);
			//			if (secret == null)
			String secret = sessionData.getOweraParameters().getValue(SystemParameters.SECRET);
			if (secret != null && secret.length() > 16 && !passwordMd5(username, secret, method, uri, nonce, nc, cnonce, qop).equals(response))
				secret = secret.substring(0, 16);
			//			if (secret == null)
			//				secret = sessionData.getOweraParameters().getValue(SystemParameters.TR069_SECRET);
			if (secret == null) {
				throw new TR069AuthenticationException("No ACS Password found in database (CPE IP address: " + reqRes.getReq().getRemoteHost() + ") (username: " + username + ")", null,
						HttpServletResponse.SC_FORBIDDEN);
			} else {
				String sharedSecretMd5 = passwordMd5(username, secret, method, uri, nonce, nc, cnonce, qop);
				if (!sharedSecretMd5.equals(response)) {
					throw new TR069AuthenticationException("Incorrect ACS Password (CPE IP address: " + reqRes.getReq().getRemoteHost() + ") (username: " + username + ")", null,
							HttpServletResponse.SC_FORBIDDEN);
				} else {
					Log.notice(DigestAuthenticator.class, "Authentication verified (CPE IP address: " + reqRes.getReq().getRemoteHost() + ")");
					return true;
				}
			}
		} catch (NoAvailableConnectionException e) {
			throw new TR069AuthenticationException("Authentication failed because of no available database connections  (CPE IP address: " + reqRes.getReq().getRemoteHost() + ") (username: "
					+ username + ")", e, HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (SQLException e) {
			throw new TR069AuthenticationException("Authentication failed because of database error (CPE IP address: " + reqRes.getReq().getRemoteHost() + ") (username: " + username + ")", e,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} catch (NoDataAvailableException e) {
			throw new TR069AuthenticationException("Authentication failed because unitid was not found (CPE IP address: " + reqRes.getReq().getRemoteHost() + ") (username: " + username + ")", e,
					HttpServletResponse.SC_FORBIDDEN);
		}
	}
}
