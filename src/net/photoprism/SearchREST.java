package net.photoprism;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * <p>A servlet to run searches of various types and return a set of
 * IDs.  Stripped down version of SearchServlet.java that returned
 * many different output types.</p>
 *
 * @author Mikey Dickerson
 * @version 20200201
 */


public class SearchREST extends PhotoServlet
{
  private static final long serialVersionUID = 1L;

  /**
   * doGet() expects exactly one of the following parameters:
   *
   * <ul>
   * <li><code>d</code> can be a database-formatted date (2004-07-01)
   * <li><code>l</code> can be a LocationID, and will find all
   * the pictures from that location.
   * <li><code>p</code> can be a PersonID, and will display all the 
   * pictures containing that person.
   * </ul>
   *
   * <p>The output is a JSON object.</p>
   *
   * <p>Whatever kind of search is requested, the results will be
   * filtered according to the tokens presented with the request.<p>
   *
   * @param req HttpServletRequest
   * @param res HttpServletResponse
   */

  public void doGet(HttpServletRequest req, HttpServletResponse res)
  throws IOException
  {
    // retry the database connection if we don't have one
    if (db == null) getDBConnection();

    List<Integer> view_tags;
    try { view_tags = getPerms(req).viewableTags(); }
    catch (SQLException e) {
       res.sendError(SC_SERVFAIL, e.toString());
       return;
    }
    if (view_tags == null || view_tags.size() == 0) {
       res.sendError(SC_FORBIDDEN, "no valid tokens");
       return;
    }

    List<Integer> ids = null;
    String q = null;
    try {
       q = PhotoUtils.sanitizeStr(req.getParameter("d"));
       if (q != null) {
          ids = searchDb(view_tags, "date(ts)='" + q + "'");
       }
       q = PhotoUtils.sanitizeInt(req.getParameter("l"));
       if (q != null) {
          ids = searchDb(view_tags, "locationid=" + q);
       }
       q = PhotoUtils.sanitizeInt(req.getParameter("p"));
       if (q != null) {
          ids = searchDb(view_tags, "s.subject=" + q);
       }
       q = PhotoUtils.sanitizeInt(req.getParameter("t"));
       if (q != null) {
          ids = searchDb(view_tags, "tag.id=" + q);
       }
       q = PhotoUtils.sanitizeInt(req.getParameter("n"));
       if (q != null) {
          ids = searchDb(view_tags, "newimage=true");
       }
    } catch (SQLException e) {
       res.sendError(SC_SERVFAIL, e.toString());
       return;
    }

    if (ids == null) {
       // note that ids == null isn't the "0 results found" case
       res.sendError(SC_BAD_REQUEST, "no search to do");
       return;
    }

    JSONArray ret = new JSONArray();
    for (int i : ids) ret.put(i);
    sendJSON(res, ret);
  }

  /**
   * method to turn an arbitrary SQL WHERE clause into an array
   * of matching ImageIDs.  See code for the tables that you can
   * pick from.
   *
   * @param where a SQL WHERE fragment (e.g. 'Date = 2004-07-01').
   * Important that it not be user supplied -- not escaped.
   * @param tags List of tags to assume visible in results
   * @return array of int imageIDs
   * @exception SQLException if anything goes wrong with the database
   */

   public static List<Integer> searchDb(List<Integer> tags, String where)
   throws SQLException
   {
      // first concoct a SQL query with the supplied pieces
      PreparedStatement st = db.prepareStatement
         ("SELECT imageid FROM image i " +
          "INNER JOIN imagetag it ON i.imageid = it.image " +
          "INNER JOIN tag ON it.tag = tag.id " +
          "LEFT JOIN " +
          "(imagesubject INNER JOIN person ON personid=subject) s " +
          "ON s.image = i.imageid " +
          "LEFT JOIN location l ON i.location = l.locationid " +
          "WHERE i.deleted IS NULL AND it.tag = ANY(?) " +
          "AND (" + where + ") ORDER BY i.ts;");
      st.setArray(1, db.createArrayOf("INTEGER", tags.toArray()));

      // use a HashSet to dedup imageIDs (repetition can happen because
      // multiple tokens can provide a path to the same image), then copy
      // into an int array
      HashSet<Integer> ids = new HashSet<Integer>();
      ResultSet rs = st.executeQuery();
      while (rs.next()) ids.add(rs.getInt(1));
      rs.close();
      st.close();
      return new ArrayList<Integer>(ids);
   }

}
