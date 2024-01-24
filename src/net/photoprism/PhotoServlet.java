package net.photoprism;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.photoprism.TokenUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A parent class for the photo database servlets to extend.  Does
 * nothing useful by itself.
 *
 * <p>Copyright &copy; 2001 Michael A. Dickerson</p>
 * <ul>
 * <li>16 Sep 00 MAD: first version</li>
 * <li>6 Apr 03 MAD: add sendFile method, moved from ImageServlet</li>
 * <li>1 Jul 04 MAD: removed imageLink() method, made db static</li>
 * <li>6 Jan 20 MAD: stripped lots of code made obsolete by tag scheme</li>
 * </ul>
 *
 * @author Michael A. Dickerson
 * @version 20200106
 */

@SuppressWarnings("serial")
public class PhotoServlet extends HttpServlet
{

  /**
   * init checks for a debug flag and prepares a database connection
   *
   * @param config ServletConfig to look at
   * @exception ServletException which should never happen
   */

  public void init(ServletConfig config) throws ServletException
  {
    super.init(config);

    // check for the debug flag in init parameters
    debug = (config.getInitParameter("debug") != null);
    if (debug) log("debug flag set");

    // check for the tuning flag
    tuning = (config.getInitParameter("tuning") != null);
    if (tuning) log ("tuning flag set");

    // 24 May 01 MAD: we'll use the PhotoUtils class instead
    // to remember this stuff, since not all programs get
    // to use init parameters
    if (!getDBConnection()) log("database connection failed!");
  }

  /** 
   * Attempts to connect to the SQL database.  Should be called at
   * servlet init but might be retried later if the connection is lost
   * for some reason.  Writes any errors to the servlet log.
   *
   * @param none
   * @return true on success
   */

  public boolean getDBConnection()
  {
    try { db = PhotoUtils.getConnection(); }
    catch (SQLException e) {
      // 1 Jul 04 MAD: can't call log() if this is a static method
      // 9 Aug 04 MAD: but why was this a static method?
      log("Unable to open database: " + e.getMessage());
      return false;
    }
    return true;
  }

  /**
   * destroy closes the presumably open database connection
   */

  public void destroy()
  {
    try { db.close(); }
    catch(Exception e) {
      log("closing database connection: " + e);
    }
  }

  /**
   * sends a file to a ServletResponse.  sendFile() will set the status
   * code, mime type, and content length.
   *
   * @param f File to send
   * @param r HttpServletResponse to write to
   * @param mimeType String to set response Content-Type to
   */

  protected void sendFile(File f, HttpServletResponse r, String mimeType)
  {
    r.setStatus(HttpServletResponse.SC_OK);
    r.setContentType(mimeType);
    r.setContentLength((int)f.length());

    // 2 Oct 05 MAD: it was previously noticed that rewriting the copyFile()
    // method to use 1k buffers made uploading much faster (there is no good
    // reason, since the BufferedStreams should have taken care of it), but
    // maybe doing the same here will help?

    BufferedOutputStream out = null;
    FileInputStream in = null;
    byte[] b = new byte[1024];

    try {
      out = new BufferedOutputStream(r.getOutputStream());
      in = new FileInputStream(f);
      while (true) {
        int bytes = in.read(b);
        if (bytes == -1) break;
        out.write(b, 0, bytes);
      }
    } catch (IOException e) {
      log("on sendFile(" + f +"): " + e);
    } finally {
      // 2 Oct 05 MAD: processor threads are leaving open handles to
      // cache files all over the place: could this be a memory leak?
      try {
        in.close();
        out.close();
      } catch (IOException forgetit) { }
    }

  }

  /**
   * serializes a JSON object and sends it as a response body with
   * correct content-type, length, and SC_OK.
   */

  protected void sendJSON(HttpServletResponse res, JSONObject obj)
  throws IOException
  {
    sendString(res, obj.toString(), "text/json");
  }

  /**
   * serializes a JSON array and sends it as a response body with
   * correct content-type, length, and SC_OK.  (Stupid that JSONObject
   * and JSONArray have no common type or interface.)
   */

  protected void sendJSON(HttpServletResponse res, JSONArray ary)
  throws IOException
  {
     sendString(res, ary.toString(), "text/json");
  }

  /**
   * sends any arbitrary String as the response body with the
   * contenttype you specify, correct length, and SC_OK.
   */

  protected void sendString
  (HttpServletResponse res, String s, String contenttype)
  throws IOException
  {
    res.setStatus(SC_OK);
    res.setContentType(contenttype);
    res.setContentLength(s.length());
    res.getWriter().write(s);
    res.getWriter().close();
  }

  /**
   * for timing code execution: a servlet method should call
   * timeCheckpoint(null) at the beginning of the interesting section
   * of code, then call timeCheckpoint("message") as many times as
   * desired.
   *
   * @param msg String for log message, or null to initialize
   * @return nothing
   */

  protected void timeCheckpoint(String msg)
  {
    if (!tuning) return;
    long now = System.currentTimeMillis();
    if (msg == null || lastTime == 0) {
      lastTime = now;
      return;
    }
    long mem = Runtime.getRuntime().freeMemory();
    // For Christ's sake, was it really necessary to make up 15 classes
    // in order to imitate printf (with none of the convenience)?  Fuck it.
    log((now - lastTime) + "ms: " + msg + " (" + mem + " bytes free)");
    lastTime = now;
  }

   /**
    * helper method because the PhotoUtils.Perms object is always
    * instantiated off of this cookie in practice.  Did not put in
    * PhotoUtils so that class would not end up dependent on the
    * javax.servlet API.
    *
    * @param just the HttpServletRequest where we will get the cookie
    * @return a TokenUtils.Perms object
    */

   protected TokenUtils.Perms getPerms(HttpServletRequest req)
   throws SQLException
   {
      return new TokenUtils.Perms(findCookie(req, "token"), db);
   }

   /**
    * helper method for cookie-based lookups such as gallery and
    * stylesheet
    *
    * @param req an HttpServletRequest
    * @param name String name of cookie to find
    * @return a String or "" if there is none.
    */

   protected static String findCookie(HttpServletRequest req, String name)
   {
      Cookie[] cookies = req.getCookies();
      if (cookies != null && name != null) {
         for (int i = 0; i < cookies.length; i++) {
            if (name.equals(cookies[i].getName())) {
               return cookies[i].getValue();
            }
         }
      }
      return "";
   }

  /**
   * the connection to the photo database
   */

  protected static Connection db;

  /**
   * servlets should be more loquacious when debug is true
   */

  protected boolean debug = false;

  /**
   * servlets should time their interesting operations when tuning is true
   */

  protected boolean tuning = false;

  /**
   * for timing operations, a place to remember a previous clock value
   */

  protected long lastTime = 0;

  /**
   * just tired of typing these out a billion times
   */

  protected static final int SC_OK =
     HttpServletResponse.SC_OK;
  protected static final int SC_FORBIDDEN =
     HttpServletResponse.SC_FORBIDDEN;
  protected static final int SC_BAD_REQUEST =
     HttpServletResponse.SC_BAD_REQUEST;
  protected static final int SC_SERVFAIL =
     HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

}

