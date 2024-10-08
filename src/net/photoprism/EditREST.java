package net.photoprism;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.photoprism.Photo;
import org.json.JSONObject;

/**
 * <p>A servlet to create a REST API for a jquery implementation
 * of the "browse" page.</p>
 *
 * @author Mikey Dickerson
 * @version 20190219
 */

public class EditREST extends PhotoServlet
{

   public void init(ServletConfig config) throws ServletException
   {
      super.init(config);
   }

   /**
    * doGet() returns a bag of all the odds and ends that are needed
    * to render the page to edit one individual photo.  The JSON
    * object is very long and complicated, but it is all generated by
    * the Photo class, so all that code and documentation(?) is over
    * there.
    */

	public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException
   {
      Photo p;
      try {
         int imgID = Integer.parseInt(req.getParameter("img"));
         p = Photo.fetchOne(db, getPerms(req), imgID);
         p.populateAll(db);
         sendJSON(res, p.toJSON());
      } catch (NumberFormatException e) {
         res.sendError(SC_BAD_REQUEST, "bad img parameter");
      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
      }
   }   


   private final String[][] FIELDS = {
      { "apt", "aperture",      "32" },
      { "cam", "camera",        "32" },
      { "exp", "exposure",      "64" },
      { "fil", "originalfile", "255" },
      { "flm", "film",          "32" },
      { "foc", "focallength",   "64" },
      { "met", "metering",      "32" },
      { "pcs", "process",       "32" },
      { "sht", "shutter",       "32" },
      { "tit", "title",        "100" },
      { "tz",  "timezone",     "100" },
      { "wmk", "watermark",     "64" }
   };

   /**
    * doPost() is where you send updates.  Your POST needs to include
    * a parameter named 'id' which specifies one image, and a bag of
    * tokens (in cookie) that proves permission to edit that image.
    * After this, an arbitrary number of parameters named after the
    * JSON elements can be supplied.
    *
    * @return HTTP 200 if all updates were committed, HTTP 400/500
    * otherwise (and no updates should be committed).  Response body
    * is "null" unless you updated the location code.  In that case
    * response body is a new bag of adjacent location nodes.
    */

	public void doPost(HttpServletRequest req, HttpServletResponse res)
   throws IOException
   {
      // validate the imgID parameter and your ability to write to it
      TokenUtils.Perms perms;
      int imgID;
      int p;
      try {
         perms = getPerms(req);
         imgID = Integer.parseInt(req.getParameter("img"));
         p = perms.imgPerm(imgID, db);
      } catch (NumberFormatException e) {
         res.sendError(SC_BAD_REQUEST, "bad img parameter");
         return;
      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      }
      if (p < TokenUtils.Perms.WRITE) {
         res.sendError(SC_FORBIDDEN);
         return;
      }

      // these will collect things of the form [...SET] xyz='abc'
      ArrayList<String> set_image = new ArrayList<String>();
      ArrayList<Integer> person_add = new ArrayList<Integer>();
      ArrayList<Integer> person_del = new ArrayList<Integer>();
      ArrayList<Integer> tag_add = new ArrayList<Integer>();
      ArrayList<Integer> tag_del = new ArrayList<Integer>();
      int new_loc = 0;

      Photo.NullBool newflag = new Photo.NullBool();
      Photo.NullBool delflag = new Photo.NullBool();

      // old timey Java, clunkity clunk
      for (Enumeration<String> en = req.getParameterNames();
           en.hasMoreElements(); ) {
         String k = en.nextElement();
         String v = req.getParameter(k);

         switch(k) {
         case "img":
            break;
         case "new":
            // didn't try this and it might not work lol
            if ("true".equals(v) || "false".equals(v)) {
               set_image.add("newimage=" + PhotoUtils.quoteString(v));
               newflag.set("true".equals(v));
            } else {
               res.sendError(SC_BAD_REQUEST, "new must be true or false");
               return;
            }
            break;
         case "del":
            if ("true".equals(v)) {
               set_image.add("deleted=now()");
               delflag.set(true);
            } else if ("false".equals(v)) {
               set_image.add("deleted=null");
               delflag.set(false);
            } else {
               res.sendError(SC_BAD_REQUEST, "del must be true or false");
               return;
            }
            break;
         case "cap":
            set_image.add("caption=" + PhotoUtils.quoteString(v));
            break;
         case "fls":
            if ("true".equals(v) || "false".equals(v)) {
               set_image.add("flash=" + PhotoUtils.quoteString(v));
            } else {
               res.sendError(SC_BAD_REQUEST, "fls must be true or false");
               return;
            }
            break;
         case "ts":
            set_image.add("ts=" + PhotoUtils.quoteString(v));
            break;
         case "rot":
            set_image.add("rotation=" + PhotoUtils.sanitizeInt(v));
            break;
         case "loc":
            set_image.add("location=" + PhotoUtils.sanitizeInt(v));
            new_loc = Integer.parseInt(v);
            break;
         case "loc_fre":
            try {
               new_loc = addFreeformLocation(v, imgID, db);
               set_image.add("location=" + new_loc);
            } catch (SQLException e) {
               res.sendError(SC_SERVFAIL, "from addFreeformLocation: " + e);
               return;
            }
            break;
         case "ppl":
            person_add.add(Integer.parseInt(v));
            break;
         case "ppl_del":
            person_del.add(Integer.parseInt(v));
            break;
         case "ppl_fre":
            try { person_add.add(addFreeformPerson(v)); }
            catch (SQLException e) {
               res.sendError(SC_SERVFAIL, "from addFreeformPerson: " + e);
               return;
            }
            break;
         case "tag":
            tag_add.add(Integer.parseInt(v));
            break;
         case "tag_del":
            tag_del.add(Integer.parseInt(v));
            break;
         case "tag_fre":
            try { tag_add.add(addFreeformTag(v, req)); }
            catch (SQLException e) {
               res.sendError(SC_SERVFAIL, "from addFreeformTag: " + e);
               return;
            }
            break;
         case "exf":
            try { ExifReader.updateExif(imgID, db); }
            catch (IOException | SQLException e) {
               res.sendError(SC_SERVFAIL, "in updateExif: " + e);
               return;
            }
            break;
         default:
            // look in the FIELDS table for a large number of varchar
            // fields that have no validation except max length
            boolean ok = false;
            for (String[] f : FIELDS) {
               if (f[0].equals(k)) {
                  if (v.length() <= Integer.parseInt(f[2])) {
                     set_image.add(f[1] + "=" + PhotoUtils.quoteString(v));
                     ok = true;
                     break;
                  } else {
                     res.sendError(SC_BAD_REQUEST, k + " too long");
                     return;
                  }
               }
            }
            if (!ok) {
               res.sendError(SC_BAD_REQUEST, "wut is " + k);
               return;
            }
         }
      }

      // touching anything at all causes newimage to be set to false
      if (newflag.isnull &&
          set_image.size() + tag_add.size() + person_add.size() > 0) {
         set_image.add("newimage='false'");
         newflag.set(false);
      }

      // and now to deal with the requested changes
      String sql = null;

      try {
         db.setAutoCommit(false);
         PreparedStatement st;

         if (set_image.size() > 0) {
            st = db.prepareStatement
               ("UPDATE image SET " + String.join(", ", set_image) +
                " WHERE imageid = " + imgID + ";");
            st.executeUpdate();
            st.close();
         }

         for (int i : person_add) {
            // we check here to prevent duplicate entries in the
            // join table
            sql = "SELECT psid FROM imagesubject " +
               "WHERE image=? AND subject=?;";
            st = db.prepareStatement(sql);
            st.setInt(1, imgID);
            st.setInt(2, i);
            if (st.executeQuery().next()) {
               throw new SQLException("duplicate person");
            }
            sql = "INSERT INTO imagesubject (image, subject) " +
               "VALUES (?, ?);";
            st = db.prepareStatement(sql);
            st.setInt(1, imgID);
            st.setInt(2, i);
            st.executeUpdate();
            st.close();
         }

         for (int i : person_del) {
            sql = "DELETE FROM imagesubject WHERE image=? AND subject=?;";
            st = db.prepareStatement(sql);
            st.setInt(1, imgID);
            st.setInt(2, i);
            st.executeUpdate();
            st.close();
         }

         for (int i : tag_add) {
            TagREST.tryAddTag(imgID, i, req, db);
         }

         for (int i : tag_del) {
            TagREST.tryDeleteTag(imgID, i, req, db);
         }

         db.commit();

      } catch (SQLException e) {
         log("on commit: " + e);
         try { db.rollback(); }
         catch (SQLException nowwhat) { log("on rollback: " + nowwhat); }
         res.sendError(SC_SERVFAIL, e.toString() + "\n" + sql);
         return;
      } finally {
         try { db.setAutoCommit(true); }
         catch (SQLException e) { log("on setAutoCommit: " + e); }
      }

      // now attempt to construct a return object that contains only
      // the structures that were modified by your POST, so that the
      // UI can update itself efficiently.
      Photo ret;
      JSONObject j = new JSONObject();
      try {
         ret = Photo.fetchOne(db, perms, imgID);
         if (new_loc > 0) {
            ret.location = new Photo.LocationNode(new_loc, null);
            ret.populateLocations(db);
            j.put("loc", ret.locationToJson());
         }
         if (tag_add.size() + tag_del.size() > 0) {
            ret.populateTags(db);
            j.put("tag", ret.tagsToJson());
         }
         if (person_add.size() + person_del.size() > 0) {
            ret.populatePeople(db);
            j.put("ppl", ret.peopleToJson());
         }
      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      }
      if (!newflag.isnull) j.put("new", newflag.val);
      if (!delflag.isnull) j.put("del", delflag.val);
      sendJSON(res, j);
   }

   /**
    * a "freeform" location edit means that somebody typed random text
    * into a box, and we want to interpret it.
    */

   public static int addFreeformLocation
      (String text, int imgID, Connection db)
      throws SQLException
   {
      int new_loc = 0;

      // first see if a node with this name already exists as a sibling
      // or a child of the current node.  In that case we assume that's
      // the one you wanted.

      String sql = "SELECT locationid, parent FROM image " +
         "INNER JOIN location ON image.location=location.locationid " +
         "WHERE image.imageid = ?;";
      PreparedStatement st = db.prepareStatement(sql);
      st.setInt(1, imgID);
      ResultSet rs = st.executeQuery();
      if (!rs.next()) throw new SQLException("bootleg imgID");
      int cur_loc = rs.getInt(1);
      int parent = rs.getInt(2);
      rs.close();

      sql = "SELECT locationid FROM location " +
         "WHERE UPPER(description) = UPPER(?) AND " +
         "(parent = ? OR parent = ?);";
      st = db.prepareStatement(sql);
      st.setString(1, text);
      st.setInt(2, cur_loc);
      st.setInt(3, parent);
      rs = st.executeQuery();
      if (rs.next()) new_loc = rs.getInt(1);
      rs.close();

      if (new_loc > 0) return new_loc;

      // no such place exists; create a new one that we assume goes
      // underneath the picture's current location.

      sql = "INSERT INTO location (description, parent) " +
         "VALUES (?, ?) RETURNING locationid;"; // look out, postgres only
      st = db.prepareStatement(sql);
      st.setString(1, text);
      st.setInt(2, cur_loc);
      rs = st.executeQuery();
      if (rs.next()) new_loc = rs.getInt(1);
      rs.close();

      return new_loc;
   }

   /**
    * adding a "freeform" person tag means that somebody typed random
    * text into a box, and we want to interpret it as a person.
    */

   private int addFreeformPerson(String text) throws SQLException
   {
      int personid = lookForPerson(text);
      if (personid > 0) return personid;

      // no good, we are creating a new person which is hopefully not
      // trash data
      String[] parts = text.split(" ");
      String fname = String.join
         (" ", Arrays.copyOfRange(parts, 0, parts.length - 1));
      String lname = parts[parts.length - 1];

      PreparedStatement st = db.prepareStatement
         ("INSERT INTO person (firstname, lastname) VALUES (?, ?);");
      st.setString(1, fname);
      st.setString(2, lname);
      if (debug)
         log("addFreeformPerson INSERT fname=" + fname + " lname=" + lname);
      st.executeUpdate();

      // we better be able to find it now
      return lookForPerson(text);
   }

   private int lookForPerson(String name) throws SQLException
   {
      int personid = 0;
      String sql = "SELECT personid FROM person " +
         "WHERE UPPER(CONCAT(firstname, ' ', lastname)) " +
         " = UPPER(?);";
      PreparedStatement st = db.prepareStatement(sql);
      st.setString(1, name);
      if (debug) log("lookForPerson name=" + name);
      ResultSet rs = st.executeQuery();
      if (rs.next()) personid = rs.getInt(1);
      rs.close();
      return personid;
   }

   /**
    * a freestyle tag request is a lot more involved, because first
    * we have to search for existing tags among only those were we
    * have write permission, then create a new one if needed.
    */

   private int addFreeformTag(String text, HttpServletRequest req)
   throws SQLException
   {
      int tagid = TokenUtils.lookupTag(text, db);
      if (tagid < 0) {
         // no tag exists by this name; create one
         if (text.length() > 15)
            throw new IllegalArgumentException("max tag length is 15");
         PreparedStatement st = db.prepareStatement
            ("INSERT INTO tag (tag, description) VALUES (?, 'new tag') " +
             "RETURNING id;"); // look out, postgres only
         st.setString(1, text);
         ResultSet rs = st.executeQuery();
         if (!rs.next()) {
            throw new SQLException("wut, no id returned");
         } else{
            tagid = rs.getInt(1);
         }
         rs.close();
      } else {
         // this tag already exists; check if you are allowed to apply it.
         if (!TokenUtils.canAddTag(tagid, findCookie(req, "token"), db)) {
            throw new IllegalArgumentException("not allowed to add tag");
         }
      }
      return tagid;
   }

}
