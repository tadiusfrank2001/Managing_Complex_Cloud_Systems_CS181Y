package net.photoprism;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * <p>A servlet to create a REST API for a jquery implementation
 * of the "browse" page.</p>
 *
 * @author Mikey Dickerson
 * @version 20190219
 */

public class BrowseREST extends PhotoServlet
{

   /**
    *
    * The returned json object has these attributes:
    *
    * title: UI text to describe this content
    * params: the bag of request parameters as we received it
    * imgs: list of (hdr | thumb | detail) objects
    *    hdr: identify by "hdr" attribute
    *       hdr: UI text to indicate a page break
    *    thumb: identify by "count" attribute
    *       id: imageID to display
    *       h, w: the native height and width of this image
    *       count: how many images are in this group
    *       text: UI text to describe this group
    *       act: fragment string that fetches this group
    *    detail: identify if object is neither hdr noor thumb
    *      id: imageID
    *      cap: caption text
    *      lid: locationID
    *      loc: location text
    *      ppl: list of { id: 1, name: 'xyz' } objects
    *      tag: list of { tag: 'xyz', lvl: 'r|d|w' } objects
    *      many other attributes; see renderDetails()
    *      
    */
   
	public void doGet(HttpServletRequest req, HttpServletResponse res)
   throws IOException
	{
      // new permission scheme reads everything from the "token" cookie
      // on every request; there is no server-side state.
      TokenUtils.Perms perms;
      try { perms = getPerms(req); }
      catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      }
      if (perms == null || perms.size() == 0) {
         res.sendError(SC_FORBIDDEN, "no valid tokens");
         return;
      }

		String mode = req.getParameter("mode");

      // here is where to write your output
      JSONObject ret = new JSONObject();
      ret.put("title", "you forgot to set title lol");

      // we are going to stuff all the parameters we received into
      // the response as "params".  this helps a page remember past
      // views and recreate them with e.g. back button
      JSONObject params = new JSONObject();
      Enumeration names = req.getParameterNames();
      while (names.hasMoreElements()) {
         String n = (String) names.nextElement();
         params.put(n, req.getParameter(n));
      }
      ret.put("params", params);

      try {
         if ("r".equals(mode)) {
            // recent
            ret.put("title", "Most Recent");
            PreparedStatement st = prepStatement(perms, SQL_RECENT);
            makeListFromSt(st, ret);
            st.close();
         } else if ("m".equals(mode)) {
            // by month
            ret.put("title", "Choose Month");
            PreparedStatement st = prepStatement(perms, SQL_MONTH);
            JSONArray imgs = new JSONArray();
            String group = null;
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
               if (!rs.getString(5).equals(group)) {
                  group = rs.getString(5);
                  JSONObject hdr = new JSONObject();
                  hdr.put("hdr", group);
                  imgs.put(hdr);
               }
               JSONObject img = new JSONObject();
               img.put("id", rs.getInt(1));
               img.put("count", rs.getInt(2));
               img.put("text", rs.getString(3));
               img.put("act", rs.getString(4));
               imgs.put(img);
            }
            rs.close();
            st.close();
            ret.put("images", imgs);
         } else if ("l".equals(mode)) {
            // by location
            int root = 1; // 1 is the root of the location tree
            JSONArray imgs = new JSONArray();
            try {
               root = Integer.parseInt
                  (PhotoUtils.sanitizeInt(req.getParameter("id")));
            } catch (NumberFormatException ignored) { }
            ret.put("title", renderLocation(root, perms, imgs));
            ret.put("images", imgs);
         } else if ("p".equals(mode)) {
            // by person
            ret.put("title", "Choose Person");
            PreparedStatement st = prepStatement(perms, SQL_PERSON);
            makeListFromSt(st, ret);
            st.close();
         } else if ("n".equals(mode)) {
            // return just the count of how many new
            ret.put("title", "");
            ret.put("n", SearchREST.searchDb(perms.viewableTags(),
                                             "newimage=true")
                    .size());
         } else if ("d".equals(mode)) {
            List<Integer> ids = null;
            String q = null;
            JSONArray imgs = new JSONArray();

            q = PhotoUtils.sanitizeStr(req.getParameter("date"));
            if (q != null) {
               ret.put("title", q);
               q = "date(ts)=" + PhotoUtils.quoteString(q);
               ids = SearchREST.searchDb(perms.viewableTags(), q);
            }

            q = PhotoUtils.sanitizeStr(req.getParameter("month"));
            if (q != null) {
               String[] parts = q.split("-");
               int month = Integer.parseInt(parts[1]);
               ret.put("title", MONTHS[month] + " " + parts[0]);
               String quote_mon = PhotoUtils.quoteString(q + "-01");
               String where = "i.ts >= date " + quote_mon +
                  " AND i.ts < date " + quote_mon + "+ INTERVAL '1 month'";
               ids = SearchREST.searchDb(perms.viewableTags(), where);
            }

            q = PhotoUtils.sanitizeInt(req.getParameter("person"));
            if (q != null) {
               ids = SearchREST.searchDb(perms.viewableTags(),
                                         "s.subject=" + q);
               // only translate personID to name if there were valid
               // results
               if (ids != null && ids.size() > 0) {
                  PreparedStatement st = db.prepareStatement(SQL_NAME);
                  st.setInt(1, Integer.parseInt(q));
                  ResultSet rs = st.executeQuery();
                  rs.next();
                  ret.put("title", rs.getString(1));
                  rs.close();
                  st.close();
               } else {
                  ret.put("title", "No results");
               }
            }

            q = PhotoUtils.sanitizeStr(req.getParameter("tag"));
            if (q != null) {
               ret.put("title", q);
               q = "tag.tag=" + PhotoUtils.quoteString(q);
               ids = SearchREST.searchDb(perms.viewableTags(), q);
            }

            if (req.getParameter("new") != null) {
               ret.put("title", "New");
               ids = SearchREST.searchDb(perms.viewableTags(),
                                         "newimage=true");
            }

            renderDetails(perms, ids, imgs);
            ret.put("images", imgs);
         }

         // make a last pass and fill in "h" and "w" for any that are missing
         if (ret.has("images")) {
            fillInDimensions((JSONArray)ret.get("images"));
         }

      } catch (SQLException e) {
         e.printStackTrace();
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      }

      // only now does anything actually get committed to the client
      sendJSON(res, ret);
		return;
	}

	public void destroy()
	{
		super.destroy();
	}

   
   /**
    * given a list of image IDs, get everything from the database
    * including all the EXIF photo data, location tag, and person
    * tag(s).  Render that to JSON objects and append the list to
    * imgs.
    *
    * @param perms { tagid: lvl, .. } as determined in TokenUtils
    * @param ids list of integer imageIDs you're interested in
    * @param imgs a JSONArray to append new photo detail objects to
    * @return true if any results were rendered
    * @throws SQLException
    */

   public boolean renderDetails
   (TokenUtils.Perms perms, List<Integer>ids, JSONArray imgs)
   throws SQLException
   {
      List<Photo> photos = Photo.fetchMany(db, perms, ids);
      if (photos == null) return false;
      for (Photo p : photos) { imgs.put(p.toJSON()); }
      return (photos.size() > 0);
   }

   /**
    * location searches are weird, because they are a hybrid where a
    * particular location has both pictures to show and child
    * locations.  This implementation renders the child locations
    * first, in thumbnails, and then a page break, and then the
    * pictures in full details.
    *
    *
    * @param root locationid, should be 1 if you are starting at the top
    * @param tags list of tagIDs that are considered visible
    * @param imgs where to append the image objects
    * @returns the String description of the root location, so that
    * the caller can set it in the page title or something.
    * @throws SQLException
    */

   private String renderLocation
   (int root, TokenUtils.Perms perms, JSONArray imgs)
   throws SQLException
   {
      boolean had_children = false;
      PreparedStatement st = null;
      ResultSet rs = null;
      String place_name = null;

      // we are going to need the name of the place.
      st = prepStatement(perms, SQL_LOCNAME);
      st.setInt(1, root);
      rs = st.executeQuery();
      rs.next();
      place_name = rs.getString(1);
      rs.close();

      // look for child locations.  The "images at this location"
      // query is expensive to construct, so we do it once and give it
      // to locationThumb which will use it recursively.

      PreparedStatement scoped_st = prepStatement
         (perms, "SELECT MAX(imageid), COUNT(imageid) " +
          SQL_IMAGES_SCOPED + "AND i.location=?");

      st = db.prepareStatement
         ("SELECT description, locationid FROM location WHERE parent=? " +
          "ORDER BY 1;");
      st.setInt(1, root);
      rs = st.executeQuery();
      while (rs.next()) {
         // this recursively walks the tree
         JSONObject thumb = locationThumb(scoped_st, rs.getInt(2));
         if (thumb.getInt("count") > 0) {
            // finish decorating the thumb object for display
            thumb.put("act", "dl" + rs.getInt(2));
            thumb.put("text", rs.getString(1));
            imgs.put(thumb);
            had_children = true;
         }
      }
      rs.close();
      st.close();
      scoped_st.close();

      // now look for pictures to show in detail form
      List<Integer> ids = SearchREST.searchDb(perms.viewableTags(),
                                              "l.locationid=" + root);
      if (ids.size() > 0 && had_children) {
         JSONObject hdr = new JSONObject();
         hdr.put("hdr", "Photos in " + place_name);
         imgs.put(hdr);
      }
      renderDetails(perms, ids, imgs);

      return place_name;
   }

   /**
    * the "locationThumb" for id i is max(imgid), count(imgid) for all
    * imgid that are visible to you where location = (i or any
    * descendant of i).
    *
    * This recurses and could be very expensive.  An alternative would
    * be to summon forth all the (imageid, location) tuples that you can
    * see through perms, and iterate through them all once and build a
    * histogram.
    */

   private JSONObject locationThumb(PreparedStatement scoped_st, int locid)
   throws SQLException
   {
      int imgid = 0;
      int count = 0;
      scoped_st.setInt(2, locid);
      ResultSet rs = scoped_st.executeQuery();
      if (rs.next()) {
         if (rs.getInt(2) > 0) {
            imgid = rs.getInt(1);
            count = rs.getInt(2);
         }
      }
      rs.close();

      // now look at children.  We don't want to create what could be
      // dozens of open database cursors, so we copy the list into a
      // local variable before recursing.
      ArrayList<Integer> children = new ArrayList<Integer>();
      PreparedStatement st = db.prepareStatement
         ("SELECT locationid FROM location WHERE parent=? " +
          "ORDER BY description;");
      st.setInt(1, locid);
      rs = st.executeQuery();
      while (rs.next()) { children.add(rs.getInt(1)); }
      rs.close();

      for (int i : children) {
         JSONObject obj = locationThumb(scoped_st, i);
         if (obj.getInt("id") > imgid) imgid = obj.getInt("id");
         count += obj.getInt("count");
      }

      JSONObject ret = new JSONObject();
      ret.put("id", imgid);
      ret.put("count", count);
      // note that we aren't setting act or text here
      return ret;
   }

   private class ImgDims {

      public ImgDims(int newh, int neww) {
         this.h = newh;
         this.w = neww;
      }

      public int h;
      public int w;
   }

   private void makeListFromSt(PreparedStatement st, JSONObject obj)
      throws SQLException
   {
      JSONArray imgs = new JSONArray();
      ResultSet rs = st.executeQuery();
      while (rs.next()) {
         JSONObject img = new JSONObject();
         img.put("id", rs.getInt(1));
         img.put("count", rs.getInt(2));
         img.put("text", rs.getString(3));
         img.put("act", rs.getString(4));
         imgs.put(img);
      }
      rs.close();
      obj.put("images", imgs);
   }

   /**
    * Given an "imgs" array that has at least "id" defined on each
    * entry, add "hgt" and "wid" members which are the native pixel
    * dimensions according to the database.  This is worth doing
    * because the client can calculate the page layout if it knows the
    * size/aspect ratio of the images that are going to arrive.
    * Before, I was having onload() handlers fire for every image and
    * this was very complicated and causing slow and herky-jerky page
    * rendering.
    */

   private void fillInDimensions(JSONArray imgs) throws SQLException
   {
      // note that you can't for-each a JSONArray because Java is a
      // total mess.
      Hashtable<Integer, ImgDims> dims = new Hashtable<Integer, ImgDims>();
      for (int i=0; i < imgs.length(); i++) {
         JSONObject img = (JSONObject)imgs.get(i);
         if (img.has("id")) {
            dims.put(img.getInt("id"), new ImgDims(0, 0));
         }
      }

      PreparedStatement st = db.prepareStatement
         ("SELECT imageid, height, width FROM image WHERE imageid = ANY(?);");
      st.setArray(1, db.createArrayOf("INTEGER", dims.keySet().toArray()));
      ResultSet rs = st.executeQuery();
      while (rs.next()) {
         // clunk clunk through Java's crapass non-native-type hashes
         ImgDims d = dims.get(rs.getInt(1));
         d.h = rs.getInt(2);
         d.w = rs.getInt(3);
      }
      rs.close();

      // now we have a map { imgid => (h, w) }; use it to fill out the
      // JSON object.  This could be a single loop, but at the cost of
      // running N database round trips instead of 1.  Unclear if this
      // matters.
      for (int i=0; i < imgs.length(); i++) {
         JSONObject img = (JSONObject)imgs.get(i);
         if (img.has("id")) {
            ImgDims d = dims.get(img.get("id"));
            img.put("hgt", d.h);
            img.put("wid", d.w);
         }
      }
   }

   private PreparedStatement prepStatement(TokenUtils.Perms perms, String sql)
      throws SQLException
   {
      PreparedStatement st = db.prepareStatement(sql);
      st.setArray(1, db.createArrayOf("INTEGER",
                                      perms.viewableTags().toArray()));
      return st;
   }
      
   /*
    * and now for a bunch of definitely not stored procedures
    */

   // the usefulness of holding this out for "don't repeat yourself"
   // is questionable, but messing up these lines could leak images
   // that weren't meant to be shown.
   private static String SQL_IMAGES_SCOPED =
      "FROM image i INNER JOIN imagetag it ON i.imageid = it.image " +
      "WHERE i.deleted IS NULL AND it.tag = ANY(?) ";

   private static String SQL_RECENT =
      "SELECT MIN(imageid), COUNT(imageid), " +
      "date(ts), " +
      "CONCAT('ddd', date(ts)) " +
      SQL_IMAGES_SCOPED +
      " GROUP BY 3 ORDER BY 3 DESC LIMIT 12;";

   private static String SQL_MONTH =
      "SELECT MIN(imageid), COUNT(imageid), " +
      "TO_CHAR(ts, 'Month YYYY'), " +
      "CONCAT('ddm', TO_CHAR(ts, 'YYYY-MM')), " +
      "DATE_PART('year', ts), MIN(ts) " +
      SQL_IMAGES_SCOPED +
      " GROUP BY 3, 4, 5 ORDER BY 6 DESC;";

   private static String SQL_PERSON =
      "SELECT MIN(imageid), COUNT(imageid), " +
      "CONCAT(p.firstname, ' ', p.lastname), " +
      "CONCAT('ddp', p.personid) " +
      "FROM image i INNER JOIN imagetag it ON i.imageid = it.image " +
      "INNER JOIN imagesubject ims ON ims.image = i.imageid " +
      "INNER JOIN person p ON ims.subject = p.personid " +
      "WHERE i.deleted IS NULL AND it.tag = ANY(?) " +
      " GROUP BY 3, 4, p.lastname ORDER BY p.lastname;";

   // TODO: the next two could be dangerous if called in a context
   // where the permission to read the names or locations has not been
   // checked.

   private static String SQL_NAME =
      "SELECT CONCAT(firstname, ' ', lastname) " +
      "FROM person WHERE personid = ?;";

   private static String SQL_LOCNAME =
      "SELECT description FROM location WHERE locationid = ?;";

   private static String SQL_LOCCHILDREN =
      "SELECT MIN(imageid), COUNT(*), l.description, l.locationid " +
      "FROM image i " +
      "INNER JOIN imagetag it ON i.imageid = it.image " +
      "LEFT JOIN location l ON i.location = l.locationid " +
      "WHERE i.deleted IS NULL AND it.tag = ANY(?) " +
      "AND l.parent = ? GROUP BY 3, 4 ORDER BY 3;";

   private static String[] MONTHS =
   {"", "January", "February", "Smarch", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"};
   // lousy smarch weather

}
