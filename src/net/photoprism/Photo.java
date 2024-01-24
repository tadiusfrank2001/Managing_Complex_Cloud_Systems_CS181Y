package net.photoprism;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This intermediate representation presents an OO interface that is
 * easier to manipulate inside the java-flavored code, and knows how
 * to serialize itself into JSON when it's done.
 *
 * The class is unusually complicated because it supports several uses:
 *
 * + fetchMany() instantiates a whole set of mostly-populated Photo
 *   objects at once.  They come back with location, tags, and people
 *   ready for display, but no "suggestions" are populated.  This is
 *   for a read-only UI.  The other cases are optimized around this
 *   case, which creates them all with one database query.
 *
 * + fetchOne() does the same as fetchMany() but for only one at a time.
 *
 * + new Photo(id) creates an empty object.
 *
 * After the object is created, you can call populatePeople(),
 * populateTags(), or populateLocations() to expand both the Photo's
 * own metadata and a set of "suggestions" which are derived by
 * looking at nearby photos.  populateAll() does them all.
 *
 * When the Photo has what you want in it, toJSON() exports it as a
 * single JSON object.  Data that was missing is just omitted.  Trying
 * to document the output format amounts to no more than rewriting the
 * function in English, so the code in toJSON() is the documentation.
 *
 * Note that with multiple long lists of the same arguments marshalled
 * various ways in this class, we see why ORMs get created.  Why not
 * using one: author spent 8 years running Google databases, where
 * ORMs were a major problem since they tend to turn turn 8 lines of
 * code into 1000 database queries--and the author of the 8 lines has
 * zero idea what to do about it once a >1M-line code base depends on
 * the ORM.
 *
 * Same experience says that if you write queries so the planner can
 * start with a single table index walk, then it will do thousands
 * per second with no problem.  So this is the style here.  It has
 * the downside that this adapter is painfully tightly coupled to
 * both the database and REST API.
 *
 * @author Mikey Dickerson
 * @date 2020-03-31
 */

public class Photo
{
   public int id;

   public double    altitude;
   public String    aperture;
   public String    camera;
   public String    cap;
   public String    date;
   public Timestamp deleted;
   public String    exposure;
   public NullBool  flash;
   public String    file;
   public String    film;
   public String    focallen;
   public int       height;
   public NullBool  isnew;
   public double    latitude;
   public double    longitude;
   public String    metering;
   public String    process;
   public int       rotation;
   public String    shutter;
   public String    timezone;
   public String    title;
   public Timestamp ts;
   public String    watermark;
   public int       width;

   public LocationNode location;
   public int permlevel;
   public ArrayList<idThing> people;
   public ArrayList<idThing> tags;
   public idThing loc_parent;
   private HashMap<Integer, idThing> cand_people;
   private HashMap<Integer, idThing> cand_tags;
   private HashMap<Integer, idThing> cand_locations;

   public String last_sql;

   public Photo() {
      this(0);
   }

   public Photo(int imgID) {
      this.id             = imgID;
      // a weird fragile hack for rotation is to use -1 for "null",
      // because 0 is valid data.
      this.rotation       = -1;
      this.flash = new NullBool();
      this.isnew = new NullBool();
   }

   public JSONObject toJSON() {
      JSONObject j = new JSONObject();
      j.put("id",  this.id);
      j.put("apt", this.aperture);
      j.put("cam", this.camera);
      j.put("cap", this.cap);
      j.put("dat", this.date);
      j.put("del", this.deleted);
      j.put("exp", this.exposure);
      j.put("fil", this.file);
      j.put("flm", this.film);
      j.put("foc", this.focallen);
      j.put("met", this.metering);
      j.put("pcs", this.process);
      j.put("sht", this.shutter);
      j.put("tit", this.title);
      j.put("ts",  this.ts);
      j.put("tz",  this.timezone);
      j.put("wmk", this.watermark);
      j.put("hgt", this.height);
      j.put("wid", this.width);
      j.put("siz", String.format("%.1fMP %dx%d",
                                 this.width * this.height / 1000000.0,
                                 this.width, this.height));

      // these are doubles that are usually not populated.  Our
      // nonzero function can't represent an area about 4cm square at
      // (0,0).  So don't take a picture there.
      putNonzero(j, "alt", this.altitude);
      putNonzero(j, "lat", this.latitude);
      putNonzero(j, "lon", this.longitude);

      if (this.rotation >= 0) // rotation is weird and fragile remember
         j.put("rot", this.rotation);

      j.put("ppl", this.peopleToJson());
      j.put("tag", this.tagsToJson());
      j.put("loc", this.locationToJson());

      // this sucks that the booleans can't be understood by j.put
      if (!this.isnew.isnull) j.put("new", this.isnew.val);
      if (!this.flash.isnull) j.put("fls", this.flash.val);

      if (this.permlevel >= TokenUtils.Perms.DOWNLOAD) j.put("dl", true);
      if (this.permlevel >= TokenUtils.Perms.WRITE)    j.put("ed", true);
      return j;
   }

   public void populateAll(Connection db) throws SQLException {
      this.populateTimestamp(db);
      this.populateTags(db);
      this.populatePeople(db);
      this.populateLocations(db);
   }

   /**
    * The serialization of location objects is done here because it
    * wants to know about both the location tree structure (parent and
    * children) and the suggested "candidate" locations.  It doesn't
    * make sense for the LocationNode to know about suggestions.
   */

   public JSONArray locationToJson() {
      LocationNode loc = this.location;
      if (loc == null) return null;

      JSONArray ary = new JSONArray();
      JSONObject j = null;
      if (loc.me != null) {
         ary.put(loc.me.toJSON());
      }
      if (this.cand_locations != null) {
         for (idThing i : this.cand_locations.values()) {
            if (i.id != loc.me.id) {
               j = i.toJSON();
               j.put("rel", "s"); // "suggestion"
               ary.put(j);
            }
         }
      }
      if (loc.parent != null) {
         j = loc.parent.toJSON();
         j.put("rel", "p"); // "parent"
         ary.put(j);
      }
      if (loc.children != null) {
         for (idThing i : loc.children) {
            j = i.toJSON();
            j.put("rel", "c"); // "child"
            ary.put(j);
         }
      }
      return ary;
   }

   /**
    * you can retrieve just the "ppl" or "tag" JSON arrays by
    * themselves if you want.
    */

   public JSONArray peopleToJson() {
      return mergeCandidates(this.people, this.cand_people);
   }

   public JSONArray tagsToJson() {
      return mergeCandidates(this.tags, this.cand_tags);
   }

   /**
    * The way we represent tags and people is weird, but convenient
    * for the UI.  The idea is, show all the candidate tags in one
    * list, where the ones that are actually applied have "act"=1.
    * This is only invoked in the process of creating JSON output; the
    * Photo object is not modified.
    */

   private JSONArray mergeCandidates
   (ArrayList<idThing> active, HashMap<Integer, idThing> suggest)
   {
      if (active == null && suggest == null) { return null; }
      ArrayList<JSONObject> merged = new ArrayList<JSONObject>();
      // make a copy of 'suggest' if it exists.  (yes, you get
      // NullPointerException if you try to do new HashMap<>(null))
      HashMap<Integer, idThing> tmp;
      if (suggest == null) {
         tmp = new HashMap<Integer, idThing>();
      } else {
         tmp = new HashMap<Integer, idThing>(suggest);
      }
      // copy all the things in the 'active' list into merged,
      // deleting them from the temporary copy of 'suggest' as we go.
      if (active != null) {
         for (idThing i : active) {
            JSONObject j = i.toJSON();
            j.put("act", true);
            merged.add(j);
            tmp.remove(i.id);
         }
      }
      // now add anything that was left over in our copy of 'suggest'
      for (idThing i : tmp.values()) {
         JSONObject j = i.toJSON();
         j.put("act", false);
         merged.add(j);
      }
      // sort the merged list so that it is always returned in the
      // same order.
      Collections.sort(merged, new sortByText());
      return new JSONArray(merged);
   }

   public void populateTags(Connection db) throws SQLException
   {
      if (this.cand_tags != null) return;
      if (this.ts == null) this.populateTimestamp(db);

      // first populate this.tags with the tags as they are
      this.tags = new ArrayList<idThing>();
      this.last_sql = "SELECT tag.id, tag.tag " +
         "FROM imagetag INNER JOIN tag ON imagetag.tag = tag.id " +
         "WHERE imagetag.image = ?;";
      PreparedStatement st = db.prepareStatement(this.last_sql);
      st.setInt(1, this.id);
      ResultSet rs = st.executeQuery();
      while (rs.next()) { this.tags.add(new idThing(rs)); }
      rs.close();
      st.close();

      // now populate this.cand_tags with tag suggestions, which are
      // obtained by looking around at other nearby pictures
      this.cand_tags = new HashMap<Integer, idThing>();
      this.last_sql = "SELECT tag_out.id, tag_out.tag " +
         "FROM tag " +
         "INNER JOIN imagetag ON imagetag.tag=tag.id " +
         "INNER JOIN imagetag imt_out ON imagetag.image=imt_out.image " +
         "INNER JOIN image i ON imt_out.image=i.imageid " +
         "INNER JOIN tag tag_out ON imt_out.tag=tag_out.id " +
         "WHERE tag.id = ? AND i.ts > ? AND i.ts < ?;";
      st = db.prepareStatement(this.last_sql);
      st.setInt(1, this.tags.get(0).id);
      st.setTimestamp(2, new Timestamp(this.ts.getTime() - A_MONTH));
      st.setTimestamp(3, new Timestamp(this.ts.getTime() + A_MONTH));
      rs = st.executeQuery();
      while (rs.next()) {
         this.cand_tags.put(rs.getInt(1), new idThing(rs));
      }
      rs.close();
      st.close();
   }
   
   public void populatePeople(Connection db) throws SQLException
   {
      if (this.cand_people != null) return;
      if (this.tags == null) this.populateTags(db);
      if (this.ts == null) this.populateTimestamp(db);

      // first populate this.people with people as they are
      this.people = new ArrayList<idThing>();
      this.last_sql = "SELECT p.personid, " +
         "CONCAT(p.firstname, ' ', p.lastname) " +
         "FROM imagesubject INNER JOIN person p " +
         "ON imagesubject.subject = p.personid " +
         "WHERE imagesubject.image = ?;";
      PreparedStatement st = db.prepareStatement(this.last_sql);
      st.setInt(1, this.id);
      ResultSet rs = st.executeQuery();
      while (rs.next()) { this.people.add(new idThing(rs)); }
      rs.close();
      st.close();

      // now populate this.cand_people with people suggestions
      this.cand_people = new HashMap<Integer, idThing>();
      this.last_sql = "SELECT p.personid, " +
         "CONCAT(p.firstname, ' ', p.lastname) " +
         "FROM imagetag " +
         "INNER JOIN image ON imagetag.image=image.imageid " +
         "INNER JOIN imagesubject ims ON image.imageid=ims.image " + 
         "INNER JOIN person p ON ims.subject=p.personid " +
         "WHERE imagetag.tag = ? AND image.ts > ? AND image.ts < ?;";
      st = db.prepareStatement(this.last_sql);
      st.setInt(1, this.tags.get(0).id);
      st.setTimestamp(2, new Timestamp(this.ts.getTime() - A_MONTH));
      st.setTimestamp(3, new Timestamp(this.ts.getTime() + A_MONTH));
      rs = st.executeQuery();
      while (rs.next()) {
         this.cand_people.put(rs.getInt(1), new idThing(rs));
      }
      rs.close();
      st.close();
   }
   
   public void populateLocations(Connection db) throws SQLException
   {
      if (this.cand_locations != null) return;
      if (this.ts == null) this.populateTimestamp(db);
      if (this.cand_tags == null) this.populateTags(db);

      this.location.populate(db);
      this.cand_locations = new HashMap<Integer, idThing>();
      this.last_sql = "SELECT l.locationid, l.description " +
         "FROM location l " +
         "INNER JOIN image i ON l.locationid=i.location " +
         "INNER JOIN imagetag it ON it.image=i.imageid " +
         "WHERE it.tag = ? AND i.ts > ? AND i.ts < ?;";
      PreparedStatement st = db.prepareStatement(this.last_sql);
      st.setInt(1, this.tags.get(0).id);
      st.setTimestamp(2, new Timestamp(this.ts.getTime() - A_MONTH));
      st.setTimestamp(3, new Timestamp(this.ts.getTime() + A_MONTH));
      ResultSet rs = st.executeQuery();
      while (rs.next()) {
         this.cand_locations.put(rs.getInt(1), new idThing(rs));
      }
      rs.close();
      st.close();
   }

   public void populateTimestamp(Connection db) throws SQLException
   {
      if (this.ts != null) return;
      this.last_sql = "SELECT ts AT TIME ZONE timezone " +
         "FROM image WHERE imageid=?;";
      PreparedStatement st = db.prepareStatement(this.last_sql);
      st.setInt(1, this.id);
      ResultSet rs = st.executeQuery();
      rs.next();
      this.ts = rs.getTimestamp(1);
      rs.close();
      st.close();
   }

   /**
    * lots of database objects need to be represented as an (id, text)
    * pair.
    */

   public static class idThing implements Comparable<idThing>
   {
      public int id;
      public String text;

      public idThing(int i, String t) {
         this.id = i;
         this.text = t;
      }

      // here's what this will typically look like
      public idThing(ResultSet rs) throws SQLException {
         this.id = rs.getInt(1);
         this.text = rs.getString(2);
      }

      public JSONObject toJSON() {
         JSONObject j = new JSONObject();
         j.put("id", this.id);
         j.put("txt", this.text);
         return j;
      }

      public int compareTo(idThing other) {
         return this.text.compareTo(other.text);
      }
   }

   // just kill me
   private static class tagMapVal
   {
      public ArrayList<idThing> tags;
      public int level;

      public tagMapVal() {
         this.tags = new ArrayList<idThing>();
         this.level = TokenUtils.Perms.NONE;
      }
   }

   /**
    * fetchMany() is a factory that instantiates a whole set of
    * mostly-populated Photo objects at once.
    */

   public static ArrayList<Photo> fetchMany
   (Connection db, TokenUtils.Perms perms, List<Integer> ids)
   throws SQLException
   {
      if (ids == null || ids.size() == 0) return null;
      if (perms == null || perms.size() == 0) return null;

      Array ary = db.createArrayOf("INTEGER", ids.toArray());

      // we make two trips, getting the tag assignments first.  We are
      // going to iterate through a database resultset of (imageid,
      // tagid, tagtext) tuples and create a map of
      //   { imgid: [tag, tag, ...], lvl }
      // where each 'tag' is an idThing and lvl is the maximum permission
      // you have for imgid.
      HashMap<Integer, tagMapVal> tagmap =
         new HashMap<Integer, tagMapVal>();
      String sql = "SELECT it.image, t.id, t.tag FROM imagetag it " +
         "INNER JOIN tag t ON it.tag=t.id " +
         "WHERE it.image = ANY(?);";
      PreparedStatement st = db.prepareStatement(sql);
      st.setArray(1, ary);
      ResultSet rs = st.executeQuery();
      while (rs.next()) {
         int imgid = rs.getInt(1);
         int tagid = rs.getInt(2);
         int newp = perms.tag_map.getOrDefault(tagid, TokenUtils.Perms.NONE);
         // create new tagMapVal object if needed
         if (tagmap.get(imgid) == null) tagmap.put(imgid, new tagMapVal());
         tagMapVal val = tagmap.get(imgid);
         // update the tagMapVal object
         val.tags.add(new idThing(rs.getInt(2), rs.getString(3)));
         if (newp > val.level) val.level = newp;
      }
      rs.close();
      st.close();

      sql = "SELECT imageid, i.caption, i.title, " +           //  1..3
         "i.aperture, i.shutter, i.exposure, i.flash, " +      //  4..7
         "i.film, i.process, i.metering, i.camera, " +         //  8..11
         "i.focallength, i.ts, " +                             // 12..13
         "i.rotation, l.parent, i.newimage, " +                // 14..16
         "i.timezone, i.deleted, i.watermark, " +              // 17..19
         "i.originalfile, i.height, i.width, " +               // 20..22
         "TO_CHAR(i.ts, 'YYYY-MM-DD') dat, " +                 // 23..24
         "i.latitude, i.longitude, i.altitude, " +             // 25..27
         "l.locationid, l.description loc_desc, l.parent, " +  // 28..30
         "s.personid, CONCAT(s.firstname, ' ', s.lastname) fullname " +
         "FROM image i " +
         "LEFT JOIN location l ON i.location = l.locationid " + // 1-1?
         "LEFT JOIN " +                                         // 1-many
         "(imagesubject INNER JOIN person ON personid=subject) s " +
         "ON i.imageid=s.image " +
         "WHERE i.imageid = ANY(?) " +
         "ORDER BY i.ts;";
      st = db.prepareStatement(sql);
      st.setArray(1, ary);
      rs = st.executeQuery();

      ArrayList<Photo> ret = new ArrayList<Photo>();
      int lastid = 0;
      Photo p = null;
      while (rs.next()) {
         if (rs.getInt(1) != lastid) {
            p = new Photo(rs.getInt(1));
            // copy in the simple metadata fields
            p.altitude  = rs.getDouble("altitude");
            p.aperture  = rs.getString("aperture");
            p.camera    = rs.getString("camera");
            p.cap       = rs.getString("caption");
            p.date      = rs.getString("dat");
            p.deleted   = rs.getTimestamp("deleted");
            p.exposure  = rs.getString("exposure");
            p.file      = rs.getString("originalfile");
            p.film      = rs.getString("film");
            p.flash     = new NullBool(rs, "flash");
            p.focallen  = rs.getString("focallength");
            p.height    = rs.getInt("height");
            p.isnew     = new NullBool(rs, "newimage");
            p.latitude  = rs.getDouble("latitude");
            p.longitude = rs.getDouble("longitude");
            p.metering  = rs.getString("metering");
            p.process   = rs.getString("process");
            p.rotation  = rs.getInt("rotation");
            p.shutter   = rs.getString("shutter");
            p.timezone  = rs.getString("timezone");
            p.title     = rs.getString("title");
            p.ts        = rs.getTimestamp("ts");
            p.watermark = rs.getString("watermark");
            p.width     = rs.getInt("width");

            // fill in location
            p.location = new LocationNode(rs.getInt("locationid"),
                                          rs.getString("loc_desc"));

            // copy list of tags and permission level
            tagMapVal v = tagmap.get(rs.getInt(1));
            if (v == null || v.level < TokenUtils.Perms.READ) {
               // the caller asked for an image that it doesn't have
               // permission to get--something is severely bork.
               throw new SQLException("missing tag map entry");
            }
            p.tags = v.tags;
            p.permlevel = v.level;

            // create new person list, which this and following rows
            // in rs will append to.
            p.people = new ArrayList<idThing>();
            ret.add(p);
            lastid = rs.getInt(1);
         }
         // for each unique photo, rows after the first will vary only
         // in the personid and fullname columns.
         if (rs.getInt("personid") > 0) {
            p.people.add(new idThing(rs.getInt("personid"),
                                     rs.getString("fullname")));
         }
      }

      rs.close();
      st.close();
      return ret;

   }

   /**
    * fetchOne() creates one mostly-populated Photo object by id.
    */

   public static Photo fetchOne
   (Connection db, TokenUtils.Perms perms, int id)
   throws SQLException
   {
      ArrayList<Integer> ids = new ArrayList<Integer>();
      ids.add(id);
      return fetchMany(db, perms, ids).get(0);
   }

   /**
    * have to create one of these to sort JSONObjects that come from
    * idThings.
    */

   public static class sortByText implements Comparator<JSONObject>
   {
      public int compare(JSONObject a, JSONObject b) {
         return a.getString("txt").compareTo(b.getString("txt"));
      }
   }

   /**
    * this is extremely tedious but we have to keep track of the
    * difference between false and null, which JDBC has no plan for.
    */

   public static class NullBool
   {
      public boolean val;
      public boolean isnull;

      public NullBool() {
         this.isnull = true;
      }

      public NullBool(boolean v) {
         this.isnull = false;
         this.val = v;
      }

      public NullBool(ResultSet rs, String col) throws SQLException {
         this.val = rs.getBoolean(col);
         this.isnull = rs.wasNull();
      }

      public String toString() {
         if (this.isnull) {
            return null;
         } else if (this.val) {
            return "true";
         } else {
            return "false";
         }
      }

      public void set(boolean v) {
         this.isnull = false;
         this.val = v;
      }
   }

   /**
    * an object for navigating the location tree without just puking
    * out the entire thing, which could be too big.
    *
    * It consists of:
    *   parent        idThing
    *   me            idThing
    *   children List<idThing>
    */

   public static class LocationNode
   {
      public idThing me;
      public idThing parent;
      public ArrayList<idThing> children;
      public boolean populated;

      public LocationNode(int id, String desc) {
         this.me = new idThing(id, desc);
         this.populated = false;
      }

      /**
       * once populated with this.me.id, call populate() to fill in
       * the parent and child nodes.  You will save one query if you
       * have already populated this.parent.id too.
       */

      public void populate(Connection db) throws SQLException
      {
         String sql = null;
         PreparedStatement st = null;
         ResultSet rs = null;

         if (this.me.id <= 1) {
            this.me = new idThing(1, "Earth");
         }

         if (this.parent == null || this.parent.id <= 0) {
            sql = "SELECT p.locationid, p.description " +
               "FROM location me INNER JOIN location p " +
               "ON me.parent = p.locationid WHERE me.locationid=?;";
            st = db.prepareStatement(sql);
            st.setInt(1, this.me.id);
            rs = st.executeQuery();
            if (rs.next()) { this.parent = new idThing(rs); }
            rs.close();
         }

         this.children = new ArrayList<idThing>();
         int parentid = 0; // will fail harmlessly if left as 0
         if (this.parent != null) parentid = this.parent.id;

         sql = "SELECT locationid, description, parent FROM location " +
            "WHERE locationid=? OR locationid=? OR parent=? " +
            "ORDER BY 2;";
         st = db.prepareStatement(sql);
         st.setInt(1, parentid);
         st.setInt(2, this.me.id);
         st.setInt(3, this.me.id);
         rs = st.executeQuery();
         while (rs.next()) {
            int id = rs.getInt(1);
            int parent = rs.getInt(3);
            // you got into this result set in one of three ways:
            if (id == this.me.id) {
               // you are me
               this.me = new idThing(rs);
            } else if (id == parentid) {
               // you are my parent
               this.parent = new idThing(rs);
            } else if (parent == this.me.id) {
               // your parent is me: you are a child location
               this.children.add(new idThing(rs));
            } else {
               throw new SQLException("unpossible!");
            }
         }
         rs.close();
         this.populated = true;
      }

   }

   private void putNonzero(JSONObject obj, String key, Double val) {
      // this is to truncate the doubles to a reasonable 6 decimal
      // places
      if (Math.abs(val) > 0.000000001) {
         obj.put(key, Math.round(val * 1000000.0) / 1000000.0);
      }
   }

   // a month of milliseconds
   private static final long A_MONTH = (long)30 * 86400 * 1000;   
}









