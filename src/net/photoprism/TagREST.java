package net.photoprism;

import java.io.IOException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Hashtable;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.photoprism.TokenUtils;
import org.json.JSONArray;

/**
 * Servlet to add and remove assignments of tags on images (POST), or
 * provide a list of tags that are available to see or manipulate
 * under the supplied set of tokens (GET).
 *
 * Since access control is built out of tags and tokens, the rules
 * about applying or removing tags are complicated and bugs could have
 * bad consequences.
 *
 * @author Mikey Dickerson
 * @version 20200120
 */

public class TagREST extends PhotoServlet
{

   public void init(ServletConfig config) throws ServletException
   {
      super.init(config);
   }

   /**
    * doGet() returns a bag of information about the tags that are
    * available to you, to help with creating a UI.  The set of tags
    * that is "available" is tricky to define.  First we enumerate the
    * tags that your specific tokens map to.  Then we find any tags
    * that are also used on "your" images.
    *
    * The returned object is a JSON array of tag objects.  Each tag
    * object contains "id", "tag", and "lvl".  If the "v" parameter
    * exists, then each tag object contains some more attributes, and
    * in the case of a tag you own, a list of the tokens that apply to
    * that tag.
    */
   
   public void doGet(HttpServletRequest req, HttpServletResponse res)
   throws ServletException, IOException
   {
      Hashtable<Integer, TokenUtils.Tag> tagbag =
         new Hashtable<Integer, TokenUtils.Tag>();

      try {
         // first we get information on the tokens you provided.
         // TODO: consider whether including the token itself is
         // actually needed.
         ArrayList<TokenUtils.Token> supplied_tokens =
            new ArrayList<TokenUtils.Token>();
         PreparedStatement st = db.prepareStatement
            ("SELECT id, token, tag, refcode, refcount, maxref, " +
             "TO_CHAR(expiration, 'YYYY-MM-DD') expires, " +
             "level FROM token WHERE token = ANY(?) " +
             "AND (expiration IS NULL OR expiration > now());");
         st.setArray
            (1, db.createArrayOf("TEXT", findCookie(req, "token").split(":")));
         ResultSet rs = st.executeQuery();
         while (rs.next()) supplied_tokens.add(new TokenUtils.Token(rs));
         rs.close();

         // now we build tag objects that you can see through your
         // tokens.  If you have multiple tokens that map to the same
         // tag, we only remember the maximum of the permission
         // levels.
         ArrayList<Integer> tag_list = new ArrayList<Integer>();
         for (TokenUtils.Token t : supplied_tokens) {
            tag_list.add(t.tag);
         }
         st = db.prepareStatement
            ("SELECT t.id, t.tag, t.description, count(it.id) n FROM tag t " +
             "INNER JOIN imagetag it ON t.id=it.tag " +
             "WHERE t.id=ANY(?) GROUP BY 1, 2, 3;");
         st.setArray(1, db.createArrayOf("INTEGER", tag_list.toArray()));
         rs = st.executeQuery();
         while (rs.next()) {
            // this is just an 'assert' that we didn't somehow repeat
            // tag IDs, which would mean that something is severely
            // misunderstood.
            if (tagbag.containsKey(rs.getInt(1)))
               throw new SQLException("wuff");
            TokenUtils.Tag tag = new TokenUtils.Tag(rs);
            tag.count = rs.getInt("n");
            tagbag.put(tag.id, tag);
            for (TokenUtils.Token tok : supplied_tokens) {
               if (tok.tag == tag.id) {
                  if (tok.level > tag.level) tag.level = tok.level;
                  tag.tokens.put(tok.id, tok);
               }
            }
         }
         rs.close();
         st.close();

         // now we search for any other tags that your images use, and
         // add them to the bag at permission level '0' if they aren't
         // already there.
         tag_list.clear();
         for (TokenUtils.Tag t : tagbag.values())
            if (t.level >= TokenUtils.Perms.WRITE) tag_list.add(t.id);
         st = db.prepareStatement
            ("SELECT t2.id, t2.tag, t2.description, count(it2.id) n " +
             "FROM tag t1 INNER JOIN imagetag it1 ON t1.id=it1.tag " +
             "INNER JOIN imagetag it2 ON it1.image=it2.image " +
             "INNER JOIN tag t2 ON it2.tag=t2.id " +
             "WHERE t1.id = ANY(?) GROUP BY 1, 2;");
         // this is very legal & very cool
         st.setArray(1, db.createArrayOf("INTEGER", tag_list.toArray()));
         rs = st.executeQuery();
         while (rs.next()) {
            TokenUtils.Tag tag = tagbag.get(rs.getInt("id"));
            if (tag == null) {
               tag = new TokenUtils.Tag(rs);
               tag.level = TokenUtils.Perms.NONE;
               tagbag.put(rs.getInt("id"), tag);
            }
            tag.count = rs.getInt("n");
         }
         rs.close();
         st.close();

         // if 'v' for verbose is not present, return the short form of
         // just tag names, ids, and access levels.
         if (req.getParameter("v") == null) {
            JSONArray ret = new JSONArray();
            for (TokenUtils.Tag t : tagbag.values()) ret.put(t.toMinJson());
            sendJSON(res, ret);
            return;
         }

         // now we get all tokens for the tags you own, and the tags
         // you use that nobody owns.
         tag_list.clear();
         for (TokenUtils.Tag t : tagbag.values())
            if (t.level >= 3 || t.level == 0) tag_list.add(t.id);
         st = db.prepareStatement
            ("SELECT id, token, tag, refcode, refcount, maxref, " +
             "TO_CHAR(expiration, 'YYYY-MM-DD') expires, level " +
             "FROM token WHERE tag = ANY(?) " +
             "AND (expiration IS NULL OR expiration > now());");
         st.setArray(1, db.createArrayOf("INTEGER", tag_list.toArray()));
         rs = st.executeQuery();
         while (rs.next()) {
            TokenUtils.Tag tag = tagbag.get(rs.getInt("tag"));
            if (tag == null) throw new SQLException("boffo");
            if (!tag.tokens.containsKey(rs.getInt("id"))) {
               tag.tokens.put(rs.getInt("id"), new TokenUtils.Token(rs));
            }
         }
         rs.close();
         st.close();

      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, "" + e);
      }

      JSONArray ret = new JSONArray();
      for (TokenUtils.Tag t : tagbag.values()) ret.put(t.toJSON());
      sendJSON(res, ret);
   }

   /**
    * doPost() expects to have extra path info /add or /del, and the
    * parameters "img" and "tag".  img must be an integer imageID.
    * tag can be either the string name of a tag, or the integer
    * tagID.
    *
    * Response body is empty always.  Status code is one of:
    * SC_BAD_REQUEST - one of /path, img, or tag was unintelligible
    * SC_FORBIDDEN - your tokens were insufficient
    * SC_SERVFAIL - our sql did not run goodly
    * SC_OK - the action was completed (or was redundant)
    *
    * The response is SC_OK if you attempt to add a tag that already
    * exists, or delete a tag that does not exist.
    */

   public void doPost(HttpServletRequest req, HttpServletResponse res)
   throws ServletException, IOException
   {

      // validate path info and "tag" parameter
      String op = req.getPathInfo();
      String tag = req.getParameter("tag");
      if (tag == null || "".equals(tag)) {
         res.sendError(SC_BAD_REQUEST, "tag parameter missing");
         return;
      }
      int tagid = -1;
      try { tagid = Integer.parseInt(tag); }
      catch (NumberFormatException ignored) {}
      if (tagid < 0) tagid = TokenUtils.lookupTag(tag, db);

      // validate "img" parameter
      int imgid = -1;
      try { imgid = Integer.parseInt(req.getParameter("img")); }
      catch (NumberFormatException e) {
         res.sendError(SC_BAD_REQUEST, "bad or missing img parameter");
         return;
      }

      try {
         if (getPerms(req).imgPerm(imgid, db)  < TokenUtils.Perms.WRITE) {
            res.sendError(SC_FORBIDDEN, "unable to modify that image");
            return;
         }

         switch (op) {
         case "add":
            if (!tryAddTag(imgid, tagid, req, db)) {
               res.sendError(SC_FORBIDDEN, "unable to add that tag");
               return;
            }
            break;
         case "del":
            if (!tryDeleteTag(imgid, tagid, req, db)) {
               res.sendError(SC_FORBIDDEN, "unable to delete that tag");
               return;
            }
            break;
         default:
            res.sendError(SC_BAD_REQUEST, "missing or bad path info");
            return;
         }

      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, "" + e);
         return;
      }

      res.setStatus(SC_OK);
      return;
   }

   public static boolean tryDeleteTag
   (int img, int tag, HttpServletRequest req, Connection db)
   throws SQLException
   {
      if (!TokenUtils.canRemoveTag(img, tag, findCookie(req, "token"), db)) {
         return false;
      }
      if (!tagExists(img, tag, db)) return true;
      PreparedStatement st = db.prepareStatement
         ("DELETE FROM imagetag WHERE image=? AND tag=?;");
      st.setInt(1, img);
      st.setInt(2, tag);
      st.executeUpdate();
      return true;
   }

   public static boolean tryAddTag
   (int img, int tag, HttpServletRequest req, Connection db)
   throws SQLException
   {
      if (!TokenUtils.canAddTag(tag, findCookie(req, "token"), db)) {
         return false;
      }
      if (tagExists(img, tag, db)) return true;
      PreparedStatement st = db.prepareStatement
         ("INSERT INTO imagetag (image, tag) VALUES (?, ?);");
      st.setInt(1, img);
      st.setInt(2, tag);
      st.executeUpdate();
      return true;
   }

   private static boolean tagExists(int img, int tag, Connection db)
   throws SQLException
   {
      boolean tagexists = false;
      PreparedStatement st = db.prepareStatement
         ("SELECT id FROM imagetag WHERE image=? AND tag=?;");
      st.setInt(1, img);
      st.setInt(2, tag);
      ResultSet rs = st.executeQuery();
      if (rs.next()) tagexists = true;
      rs.close();
      return tagexists;
   }
}
      
