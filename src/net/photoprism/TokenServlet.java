package net.photoprism;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Base64;
import java.util.HashSet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet to validate referral codes and update the token cookie.
 *
 * @author Mikey Dickerson
 * @version 20200202
 */

public class TokenServlet extends PhotoServlet
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
   }

   public class ValidationError extends Exception
   {
      public ValidationError(String msg)
      {
         super(msg);
      }
   }

   /**
    * This endpoint does one of three things:
    *
    * + if 'rc' is present, validates that referral code, translates
    *   to a token, and puts that token in your cookie.
    * + if 'new' is present, creates a new token/code pair.
    * + if 'del' is present, deletes an existing token/code.
    */

   public void doPost(HttpServletRequest req, HttpServletResponse res)
   throws IOException
   {
      if (req.getParameter("rc") != null) {
         validateCode(req, res);
      } else if (req.getParameter("new") != null) {
         createCode(req, res);
      } else if (req.getParameter("del") != null) {
         deleteCode(req, res);
      } else {
         res.sendError(SC_BAD_REQUEST, "no action parameter received");
      }
   }

   /**
    * Expects a parameter rc for "referral code".  Checks to see if
    * it is valid.  If so, gives you back a token in the proper
    * cookie, and updates the reference count in the database.  If
    * not, nothing happens except a 403 error.
    */

   private void validateCode(HttpServletRequest req, HttpServletResponse res)
   throws IOException
   {
      // normalize to digits only
      StringBuffer b = new StringBuffer();
      for (char c : req.getParameter("rc").toCharArray()) {
         if (c >= '0' && c <= '9') b.append(c);
      }

      try { 
         PreparedStatement st = db.prepareStatement
            ("SELECT id, token, refcount, maxref, expiration, now() " +
             "FROM token WHERE refcode = ?;");
         st.setString(1, b.toString());
         ResultSet rs = st.executeQuery();
         if (!rs.next()) {
            throw new ValidationError("no such code");
         }

         // check if this code has already been redeemed too many times
         if (rs.getInt(3) >= rs.getInt(4) && !rs.wasNull()) {
            throw new ValidationError("code is exhausted");
         }

         // check if this code is past its expiration date
         Timestamp exp = rs.getTimestamp(5);
         if (exp != null && rs.getTimestamp(6).after(exp)) {
            throw new ValidationError("code is expired");
         }

         // take apart the existing token cookie if any, and add the
         // new token (not the referral code)
         HashSet<String> tset = new HashSet<String>();
         if (findCookie(req, "token") != null) {
            for (String t : findCookie(req, "token").split(":"))
               tset.add(t);
         }
         tset.add(rs.getString(2));
         Cookie c = new Cookie("token", String.join(":", tset));
         c.setPath("/");
         c.setMaxAge(86400 * 365);
         res.addCookie(c);

         // update the reference count
         st = db.prepareStatement
            ("UPDATE token SET refcount = ? WHERE id = ?;");
         st.setInt(1, rs.getInt(3) + 1);
         st.setInt(2, rs.getInt(1));
         st.execute();

      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      } catch (ValidationError e) {
         res.sendError(SC_FORBIDDEN, e.toString());
         return;
      }

      res.setStatus(SC_OK);
   }

   /**
    * createCode requres int parameters 'tag' and 'lvl'.  'cnt' is an
    * optional int that will be stored in maxref (maximum number of
    * times the code can be used).  'exp' is an optional milliseconds
    * timestamp that will be stored in expires (expiration time for
    * this code and token).
    */

   private void createCode(HttpServletRequest req, HttpServletResponse res)
   throws IOException
   {
      int tag = -1;
      int lvl = -1;
      int cnt = -1;
      Timestamp exp = null;
      // tag and lvl have to be cromulent integers.
      try {
         tag = Integer.parseInt(req.getParameter("tag"));
         lvl = Integer.parseInt(req.getParameter("lvl"));
      } catch (NumberFormatException e) {
         res.sendError(SC_BAD_REQUEST, "bad tag or lvl parameter");
         return;
      }
      if (!(lvl > TokenUtils.Perms.NONE && lvl <= TokenUtils.Perms.WRITE)) {
         res.sendError(SC_BAD_REQUEST, "lvl out of range");
         return;
      }
      // if these are missing or unparseable, we don't care.
      try { cnt = Integer.parseInt(req.getParameter("cnt")); }
      catch (NumberFormatException ignored) { }
      try { exp = new Timestamp(Long.parseLong(req.getParameter("exp"))); }
      catch (NumberFormatException ignored) { }

      // The rule for whether you can create a new code for tag t is
      // the same as the rule for whether you can apply t to an image:
      // either you have write access to "t", or nobody has write
      // access to "t".
      try {
         if (!TokenUtils.canAddTag(tag, findCookie(req, "token"), db)) {
            res.sendError(SC_FORBIDDEN);
            return;
         }
      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      }

      // newtoken is a random 72-bit number encoded as 12 digits in
      // base64.  newcode is a uniformly random number of 8 digits in
      // decimal.
      SecureRandom sr = new SecureRandom();
      byte[] bytes = new byte[9];
      sr.nextBytes(bytes);
      String newtoken = Base64.getEncoder().encodeToString(bytes);
      String newcode = Integer.toString(sr.nextInt(100000000));

      try {
         PreparedStatement st = db.prepareStatement
            ("INSERT INTO token " +
             "(token, tag, maxref, expiration, level, refcode) " +
             "VALUES (?, ?, ?, ?, ?, ?);");
         st.setString(1, newtoken);
         st.setInt(2, tag);
         if (cnt < 0) st.setNull(3, Types.INTEGER); else st.setInt(3, cnt);
         st.setTimestamp(4, exp);
         st.setInt(5, lvl);
         st.setString(6, newcode);
         st.executeUpdate();
      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      }

      res.setStatus(SC_OK);
   }

   /**
    * deleteCode requres one parameter 'del', which should match an
    * entry in the 'rc' column of the token table.
    */

   private void deleteCode(HttpServletRequest req, HttpServletResponse res)
   throws IOException
   {
      try {
         PreparedStatement st = db.prepareStatement
            ("SELECT id, tag FROM token WHERE refcode=?;");
         st.setString(1, req.getParameter("del"));
         ResultSet rs = st.executeQuery();
         if (!rs.next()) {
            res.sendError(SC_FORBIDDEN);
            return;
         }
         int tokenid = rs.getInt(1);
         int tagid = rs.getInt(2);
         rs.close();

         // the rule for whether you are allowed to delete a token is
         // the same as the rule for whether you would be allowed to
         // create that token.
         if (!TokenUtils.canAddTag(tagid, findCookie(req, "token"), db)) {
            res.sendError(SC_FORBIDDEN);
            return;
         }

         st = db.prepareStatement("DELETE FROM token WHERE id=?;");
         st.setInt(1, tokenid);
         st.executeUpdate();
      } catch (SQLException e) {
         res.sendError(SC_SERVFAIL, e.toString());
         return;
      }

      res.setStatus(SC_OK);
   }

}

