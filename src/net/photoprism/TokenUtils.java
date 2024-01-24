package net.photoprism;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;


public class TokenUtils
{

   /**
    * "Tag" objects are associated with images via the table ImageTag.
    * Tags have 0 or more related "Token"s in the table Token.
    */

   public static class Tag
   {
      public int id;
      public String tag;
      public String desc;
      public int count;
      public int level;
      public Hashtable<Integer, Token> tokens;

      public Tag(ResultSet rs) throws SQLException
      {
         this.id = rs.getInt("id");
         this.tag = rs.getString("tag");
         this.desc = rs.getString("description");
         this.level = 0;
         this.tokens = new Hashtable<Integer, Token>();
      }

      public JSONObject toJSON()
      {
         JSONArray toks = new JSONArray();
         for (Token t : this.tokens.values()) toks.put(t.toJSON());
         JSONObject j = new JSONObject();
         j.put("id", this.id);
         j.put("tag", this.tag);
         j.put("dsc", this.desc);
         j.put("n", this.count);
         j.put("lvl", this.level);
         j.put("tok", toks);
         return j;               
      }

      public JSONObject toMinJson()
      {
         JSONObject j = new JSONObject();
         j.put("id", this.id);
         j.put("tag", this.tag);
         j.put("lvl", this.level);
         return j;
      }
   }

   /**
    * "Token" objects live in the Token table.  They are random
    * strings that map to exactly one (tag, permission) pair.  They
    * have some properties of their own such as expiration date, max
    * number of uses, etc.
    */

   public static class Token
   {
      public int id;
      public int tag;
      public String token;
      public String expires;
      public String code;
      public int level;
      public int redeemed;
      public int max_redeem;

      public Token(ResultSet rs) throws SQLException
      {
         this.id = rs.getInt("id");
         this.tag = rs.getInt("tag");
         this.token = rs.getString("token");
         this.expires = rs.getString("expires");
         this.code = rs.getString("refcode");
         this.level = rs.getInt("level");
         this.redeemed = rs.getInt("refcount");
         this.max_redeem = rs.getInt("maxref");
      }

      public JSONObject toJSON()
      {
         JSONObject j = new JSONObject();
         j.put("id", this.id);
         j.put("tag", this.tag);
         j.put("tok", this.token);
         j.put("exp", this.expires);
         j.put("rc", this.code);
         j.put("n", this.redeemed);
         j.put("max", this.max_redeem);
         j.put("lvl", this.level);
         return j;
      }
   }

   public static class Perms
   {
      public Hashtable<Integer, Integer> tag_map;
      public static final int NONE = 0;
      public static final int READ = 1;
      public static final int DOWNLOAD = 2; // maybe not used?
      public static final int WRITE = 3;
      public static final int MANAGE = 4; // maybe not used?

      public Perms() {
         this.tag_map = null;
      }

      public Perms(String cookie, Connection db) throws SQLException {
         this.tag_map = new Hashtable<Integer, Integer>();
         if (cookie == null || "".equals(cookie)) return;
         PreparedStatement st = db.prepareStatement
            ("SELECT tag, level FROM token WHERE token = ANY(?) " +
             "AND (expiration IS NULL OR expiration > now());");
         st.setArray(1, db.createArrayOf("TEXT", cookie.split(":")));
         ResultSet rs = st.executeQuery();
         while (rs.next()) {
            int tag = rs.getInt(1);
            int lvl = rs.getInt(2);
            if (!this.tag_map.containsKey(tag)) {
               this.tag_map.put(tag, lvl);
            } else if (this.tag_map.get(tag) < lvl) {
               this.tag_map.put(tag, lvl);
            }
         }
         rs.close();
         st.close();
      }

      public List<Integer> filteredTags(int min_level)
      {
         ArrayList<Integer> ret = new ArrayList<Integer>();
         if (this.tag_map != null) {
            for (int tagid : this.tag_map.keySet()) {
               if (this.tag_map.get(tagid) >= min_level) ret.add(tagid);
            }
         }
         return ret;
      }

      public List<Integer> viewableTags()
      {
         return this.filteredTags(READ);
      }

      public List<Integer> writableTags()
      {
         return this.filteredTags(WRITE);
      }

      public int size()
      {
         return this.tag_map.size();
      }

      /**
       * given an imageid and a db connection, return the maximum of
       * the permission levels represented by this.tag_map.
       */

      public int imgPerm(int img, Connection db) throws SQLException
      {
         int perm = this.NONE;
         if (this.tag_map != null && this.tag_map.size() > 0) {
            PreparedStatement st = db.prepareStatement
               ("SELECT tag FROM imagetag WHERE image=?;");
            st.setInt(1, img);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
               if (this.tag_map.getOrDefault(rs.getInt(1), this.NONE) > perm) {
                  perm = this.tag_map.get(rs.getInt(1));
               }
            }
            rs.close();
            st.close();
         }
         return perm;
      }

   }

   /**
    * Check if the user presenting a given list of tokens should be
    * able to add the given tag t to a pre-existing image.  The rule
    * is: you have a write token for t OR nobody has a write token for
    * t.
    *
    * @param tagid
    * @param cookie comma-delimited list of tokens
    * @param db your database handle
    */

   public static boolean canAddTag(int tagid, String cookie, Connection db)
   throws SQLException
   {
      List<String> tokenlist = Arrays.asList(cookie.split(":"));
      PreparedStatement st = db.prepareStatement
         ("SELECT token, level FROM token WHERE token.tag=?;");
      st.setInt(1, tagid);
      ResultSet rs = st.executeQuery();
      boolean icanwrite = false;
      boolean someonecanwrite = false;
      while (rs.next()) {
         if (rs.getInt("level") >= 3) {
            if (tokenlist.contains(rs.getString("token"))) {
               icanwrite = true;
            } else {
               someonecanwrite = true;
            }
         }
      }
      rs.close();
      st.close();
      return icanwrite || !someonecanwrite;
   }

   /**
    * Check whether the user with a given list of tokens should be
    * able to delete a particular tag from a particular image.  The
    * rule is: if you still have write access to the image after
    * removing the requested tag, you can do it.  Otherwise the
    * removal should be prevented because it would create an orphaned
    * image that possibly nobody can access.
    */
   
   public static boolean canRemoveTag
   (int imgid, int tagid, String cookie, Connection db)
   throws SQLException
   {
      List<String> tokenlist = Arrays.asList(cookie.split(":"));
      PreparedStatement st = db.prepareStatement
         ("SELECT tag.id, t.token, t.level FROM token t " +
          "INNER JOIN tag ON t.tag=tag.id " +
          "INNER JOIN imagetag it ON it.tag=tag.id " +
          "WHERE it.image = ?;");
      st.setInt(1, imgid);
      ResultSet rs = st.executeQuery();
      boolean canstillwrite = false;
      while (rs.next()) {
         // if this is the tag we want to delete, skip it.
         if (rs.getInt(1) == tagid) continue;
         // otherwise see if this tag/token lets us write.
         if (tokenlist.contains(rs.getString(2)) &&
             rs.getInt(3) >= TokenUtils.Perms.WRITE) {
            canstillwrite = true;
            break;
         }
      }
      rs.close();
      return canstillwrite;
   }

   /**
    * Resolves String tag name to int tagid.  No exceptions can be thrown.
    *
    * @returns -1 on error.
    */

   public static int lookupTag(String tag, Connection db)
   {
      int tagid = -1;
      try {
         PreparedStatement st = db.prepareStatement
            ("SELECT id FROM tag WHERE tag=?;");
         st.setString(1, tag);
         ResultSet rs = st.executeQuery();
         rs.next();
         tagid = rs.getInt(1);
         rs.close();
      } catch (SQLException ignored) {}
      return tagid;
   }

}
