package net.photoprism;

import java.io.IOException;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.photoprism.LocationREST;
import org.json.JSONArray;
import org.json.JSONObject;


public class SuggestREST extends PhotoServlet
{
   public void init(ServletConfig config) throws ServletException
   {
      super.init(config);
   }

   public void doGet(HttpServletRequest req, HttpServletResponse res)
   throws IOException
   {
      JSONObject ret = new JSONObject();
      Array tag_ary = null;
      try {
         tag_ary = db.createArrayOf
            ("INTEGER", getPerms(req).writableTags().toArray());
      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, "could not resolve tokens to tags");
         return;
      }
      
      try {
         // watermark and timezone suggestions are the same:
         // the options in "opt" are all the string values previously
         // used with your tags, and the default in "dfl" is the most
         // recent of those.
         if (req.getParameter("wmk") != null) {
            suggest(ret, tag_ary, "watermark", "wmk");
         }
         if (req.getParameter("tmz") != null) {
            suggest(ret, tag_ary, "timezone", "tmz");
         }
         if (req.getParameter("loc") != null) {
            // a location bag that defaults to "earth"
            int loc = 1;
            try { loc = Integer.parseInt(req.getParameter("loc")); }
            catch (NumberFormatException ignored) { }
            ret.put("loc", LocationREST.getLocationNode(db, loc));
         }
      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
      }

      sendJSON(res, ret);

   }

   /**
    * It is important that 'dbfield' is never user supplied.  When
    * written, suggest() was only called twice with 'dbfield' and
    * 'jsonfield' hard coded.
    */

   private void suggest(JSONObject ret, Array tag_ary,
                        String dbfield, String jsonfield)
   throws SQLException
   {
      PreparedStatement st = db.prepareStatement
         ("SELECT " + dbfield + " FROM image i INNER JOIN imagetag it " +
          "ON i.imageid=it.image WHERE it.tag = ANY(?) " +
          "GROUP BY 1 ORDER BY MAX(i.imageid) DESC;");
      st.setArray(1, tag_ary);
      ResultSet rs = st.executeQuery();
      JSONObject obj = new JSONObject();
      JSONArray opt = new JSONArray();
      while (rs.next()) {
         if (opt.length() == 0) { obj.put("dfl", rs.getString(1)); }
         opt.put(rs.getString(1));
      }
      rs.close();
      obj.put("opt", opt);
      ret.put(jsonfield, obj);
   }
   
}
