package net.photoprism;

import net.photoprism.Photo;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A REST servlet to help navigate the location tree.
 *
 * @author Mikey Dickerson
 * @version 20200101
 */

public class LocationREST extends PhotoServlet
{

   public void init(ServletConfig config) throws ServletException
   {
      super.init(config);
   }

   public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException
   {
      JSONArray ret = null;
      int locID = 1;
      // it's OK if locID is missing, we will assume 1 ("Earth")
      try { locID = Integer.parseInt(req.getParameter("id")); }
      catch (NumberFormatException ignored) { }
      try { ret = getLocationNode(db, locID); }
      catch (SQLException e) { res.sendError(SC_SERVFAIL, e.toString()); }
      sendJSON(res, ret);
      return;
   }

   public static JSONArray getLocationNode(Connection db, int locID)
   throws SQLException
   {
      Photo.LocationNode ln = new Photo.LocationNode(locID, "");
      ln.populate(db);
      JSONArray ary = new JSONArray();
      JSONObject j = null;
      ary.put(ln.me.toJSON());
      if (ln.parent != null) {
         j = ln.parent.toJSON();
         j.put("rel", "p"); // "parent"
         ary.put(j);
      }
      for (Photo.idThing i : ln.children) {
         j = i.toJSON();
         j.put("rel", "c"); // "child"
         ary.put(j);
      }
      return ary;
   }
}
