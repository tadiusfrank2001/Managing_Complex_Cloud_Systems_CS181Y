package net.photoprism;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet to return an image from the database.  The doGet() method
 * does all the actual work of decoding an image, modifying it in
 * various ways, re-encoding it as a JPEG and sending it to the
 * browser.
 *
 * <ul>
 * <li>22 May 01 MAD: first version</li>
 * <li>31 May 03 MAD: add content length to response for log completeness</li>
 * <li> 6 Apr 03 MAD: remove sendFile() method to PhotoServlet</li>
 * <li>26 Aug 05 MAD: many revisions since the last entry here, but at
 *     the moment I'm trying to convert to JAI</li>
 * <li>26 Oct 07 MAD: adding delegation to S3 storage</li>
 * <li>23 Feb 19 MAD: forked to implement different tag-permission scheme</li>
 * </ul>
 *
 * @author Mikey Dickerson
 * @version 20190223
 */

public class ImageREST extends PhotoServlet
{
	/**
	 * not much work to do here, thanks to the PhotoServlet
	 *
	 * @param config as usual
	 * @exception ServletException if parent throws it
	 */

	public void init(ServletConfig config) throws ServletException
	{
		super.init(config);
		pkeepCache = config.getInitParameter("pkeepcache");
		pkeepHost = config.getInitParameter("pkeephost");
	}

	/**
	 * doGet returns an image in the appropriate mime type.  Images are
	 * converted to jpeg unless we are returning the original file, in which
	 * case the original format and mime-type are preserved.  At the
	 * present, the parameters we understand are:
	 *
	 * <ul>
	 * <li><code>id</code> the specific ImageID we want to see</li>
	 * <li><code>w</code> size to scale image to, in pixels.  supply
	 * '0' to ask for the original image file.</li>
	 * </ul>
	 *
	 * @param req the usual HttpServletRequest
	 * @param res the usual HttpServletResponse
	 * @exception ServletException can happen if parameters are borked somehow
	 * @exception IOException if something goes horribly wrong
	 */

	public void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {

		int imgID = 0;
		int size = 1024;
      StringBuffer inst = new StringBuffer();
      
		timeCheckpoint(null);

      // we expect path info to be /width/imageid[.jpg]
      if (req.getPathInfo() == null) {
         res.sendError(SC_BAD_REQUEST, "missing path");
         return;
      }
      String[] parts = req.getPathInfo().split("/");
      try { size = Integer.parseInt(parts[1]); }
      catch (NumberFormatException e) {
         res.sendError(SC_BAD_REQUEST, "non-numeric size: " + parts[1]);
         return;
      }
      if (parts.length > 2) {
         if (parts[2].endsWith(".jpg")) {
            parts[2] = parts[2].substring(0, parts[2].length() - 4);
         }
         try { imgID = Integer.parseInt(parts[2]); }
         catch (NumberFormatException e) {
            res.sendError(SC_BAD_REQUEST, "non-numeric id: " + parts[2]);
            return;
         }            
		}

		try {
         // check permission against tokens
         int p = getPerms(req).imgPerm(imgID, db);
         boolean orig = (size == 0);
         if ((p < TokenUtils.Perms.READ) ||
             (orig && p < TokenUtils.Perms.DOWNLOAD)) {
            res.sendError(SC_FORBIDDEN);
            return;
         }

         if (orig) {
            res.setHeader("Content-Disposition", "attachment; filename=" +
                          imgID + ".jpg");
         }

         // nb that the "current user" log field is obsolete but not
         // replaced with anything meaningful yet.
			if (size >= 450)
				logView(imgID, 1, size, req.getRemoteHost());

         PreparedStatement st = db.prepareStatement
            ("SELECT l.description AS loc, " +
             "i.rotation, i.caption, i.watermark, " +
             "date_part('year', i.ts) AS year, i.imageid " +
             "FROM image i " +
             "LEFT JOIN location l ON i.location=l.locationid " +
             "WHERE i.imageid=?;");
         st.setInt(1, imgID);
			ResultSet rs = st.executeQuery();
			if (!rs.next()) throw new SQLException("missing image");

         // assemble the rendering instructions which pkeep will read
			if (rs.getInt("rotation") != 0) {
				inst.append("ROTATE ");
				inst.append(rs.getInt("rotation"));
				inst.append('\n');
			}

         if (size > 400 && rs.getString("watermark") != null) {
            String wm = rs.getString("watermark");
            wm = replaceStr(wm, rs, "(year)", "year");
            wm = replaceStr(wm, rs, "(caption)", "caption");
            wm = replaceStr(wm, rs, "(id)", "imageid");
            wm = replaceStr(wm, rs, "(location)", "loc");
            inst.append("TEXT_RIGHT ");
            inst.append(wm);
            inst.append('\n');
         }

         rs.close();
         st.close();

      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      }

		// 23 Dec 09 MAD: time to simplify this.  pkeep will handle all requests;
		//                we will keep no java image stack.
		
		String url = null;
      if (debug)
         log("imgid " + imgID + " size " + size + " inst " + inst.toString());

		try {
			url = PhotoUtils.askPkeep(imgID, size, inst.toString());
		} catch (SocketTimeoutException e) {
			res.sendError(SC_SERVFAIL, "pkeep timed out on imgID " + imgID);
			return;
		} catch (SocketException e) {
			res.sendError(SC_SERVFAIL, "socket error: " + e.toString());
			return;
		} catch (UnknownHostException e) {
         String msg = "how did you manage to make localhost not resolve?";
			res.sendError(SC_SERVFAIL, msg);
			return;
		}

      if (url == null) {
         res.sendError(SC_SERVFAIL, "no url received for imgid " + imgID);
         return;
		} else if (url.startsWith("file://")) {
         // pkeep may respond with a file:// URL, in which case it
         // should have fetched the image to a location we can read.
         // This is the normal case.
			File pkeepCacheFile = new File(url.substring(7));
			if (pkeepCacheFile.canRead()) {
				sendFile(pkeepCacheFile, res, "image/jpeg");
				timeCheckpoint("sent cache file");
				return;
			} else {
            String msg = "pkeep sent us a bogus file:// url:" + pkeepCacheFile;
				log(msg);
				res.sendError(SC_SERVFAIL, msg);
				return;
			}
		} else if (url.startsWith("None") || url.startsWith("OH NOES")) {
         // pkeep has various picayune ways of expressing failure
         res.sendError(SC_SERVFAIL, url);
         return;
      } else {
			// If we receive any other URL, we send it as a 302 redirect.
			res.sendRedirect(url);
		}

	}

   /**
    * because it takes a lot of annoying boilerplate to avoid crashing
    * on NullPointerException
    */

   private static String replaceStr
   (String str, ResultSet rs, String replace_what, String replace_with)
   throws SQLException
   {
      if (rs.getString(replace_with) == null) {
         return str.replace(replace_what, "");
      } else {
         return str.replace(replace_what, rs.getString(replace_with));
      }
   }

	/**
	 * log (in a database row) the fact that an image was viewed
	 *
	 * @param img ImageID
	 * @param user PersonID of logged in user, if any
	 * @param size size of image rendered
	 * @param client probably the req.getRemoteHost()
	 */

	private static void logView(int img, int user, int size, String client)
	{
		try {
         PreparedStatement st = db.prepareStatement
            ("INSERT INTO imageviews (image, person, size, remotehost) " +
             "VALUES (?, ?, ?, ?);");
         st.setInt(1, img);
         st.setInt(2, user);
         st.setInt(3, size);
         st.setString(4, client);
			st.executeUpdate();
         st.close();
		} catch (SQLException ignored) {}
	}

	/**
	 * the local path to a pkeep-maintained cache
	 */
	public static String pkeepCache = null;
	
	/**
	 * hostname where we can find a pkeep process listening to udp
	 */
	public static String pkeepHost = null;

}
