package net.photoprism;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;


public class UploadREST extends PhotoServlet
{
   public void init(ServletConfig config) throws ServletException
   {
      super.init(config);
   }

   /**
    * Accepts a multipart-encoded request body and attempts to process
    * any uploaded files as either single jpeg images, or zip files
    * containing an arbitrary number of jpeg images.
    *
    * Many metadata fields can be specified as parameters and will be
    * stored along with all the new images: dat, loc, cam, flm, pcs,
    * tmz, tag.
    *
    * tag must be specified, and must be the integer tagid of a tag
    * that you are allowed to write, according to the token cookies
    * that were supplied.
    *
    * @param req, res
    * @exception IOException
    */

   public void doPost(HttpServletRequest req, HttpServletResponse res)
   throws IOException
   {
      NewFileMetadata md = new NewFileMetadata();
      Part part;
      MultipartParser mp;

      try {
         mp = new MultipartParser(req, PhotoUtils.UPLOAD_MAX_SIZE, true, true);
      } catch (IOException e) {
         res.sendError(SC_BAD_REQUEST, e.toString());
         return;
      }

      while ((part = mp.readNextPart()) != null ) {
         if (part.isParam()) {
            ParamPart pp = (ParamPart)part;
            String v = pp.getStringValue();
            if (debug) log("got a param: " + pp.getName() + "=" + v);
            switch (pp.getName()) {
            case "ts":
               md.timestamp = v;
               break;
            case "loc":
               try { md.location = Integer.parseInt(v); }
               catch (NumberFormatException e) {
                  md.error = "non-integer value for loc";
               }
               break;
            case "nlc":
               md.new_location = v;
               break;
            case "cam":
               md.camera = v;
               break;
            case "flm":
               md.film = v;
               break;
            case "pcs":
               md.process = v;
               break;
            case "tz":
               md.timezone = v;
               break;
            case "wmk":
               md.watermark = v;
               break;
            case "tag":
               try { md.tags.add(Integer.parseInt(v)); }
               catch (NumberFormatException e) {
                  md.error = "non-integer value for tag";
               }
               break;
            case "file":
               // If the file select input was left blank, the
               // multipart parser will misidentify it as a parameter
               // with no value.  There isn't anything useful to do,
               // but we can make the error message slightly less
               // confusing.
               md.error = "file missing";
               break;
            default:
               md.error = "got unrecognized param " + pp.getName();
            }
         } else if (part.isFile()) {
            // NB that if you see a FilePart, you have to read its data
            // and save it somewhere immediately or else it is gone when
            // you advance the MultipartParser.
            FilePart fp = (FilePart)part;
            if (fp.getFileName() != null) {
               md.fileparts.add(fp);
               md.savefiles.add(saveFilePart(fp));
            }
            if (debug) {
               log("got a file: " + fp.getFileName());
               log("content-type: " + fp.getContentType());
            }
         } else {
            res.sendError(SC_BAD_REQUEST, "unpossible part!");
            return;
         }
      }

      // ok, we parsed all the uploaded stuff.  if anything was nonsense,
      // md.error contains the last complaint.
      if (md.error != null) {
         res.sendError(SC_BAD_REQUEST, md.error);
         return;
      }

      // need to know the tag IDs that are writable, and this file
      // must have come with at least one of them.
      List<Integer> writable_tagids;
      try { writable_tagids = getPerms(req).writableTags(); }
      catch (SQLException e) {
         res.sendError(SC_SERVFAIL, "could not resolve tokens to tags");
         return;
      }

      int first_tagid = -1;
      for (int t : md.tags) {
         if (writable_tagids.contains(t)) {
            first_tagid = t;
            break;
         }
      }
      if (first_tagid == -1) {
         res.sendError(SC_BAD_REQUEST, "no writable tag specified");
         return;
      }

      // well we're at least going to try to eat these files.  Let's tell
      // the client how it goes.
      StringWriter logger = new StringWriter();
      PrintWriter out = new PrintWriter(logger);
      ArrayList<Integer> newids = new ArrayList<Integer>();

      // now go over the saved file parts
      for (int i = 0; i < md.fileparts.size(); ++i) {
         FilePart fp = md.fileparts.get(i);
         File savefile = md.savefiles.get(i);
         String ctype = fp.getContentType();

         // decide what to do based on content-type
         if (ctype == null) {
            out.println("no content-type supplied, don't know what to do.");
         } else if (ctype.equals("image/jpeg")) {
            int newid = ImageInserter.addImageByTag
               (savefile, fp.getFileName(), md.timezone, first_tagid, out, db);
            if (newid > 0) {
               out.println("new imageid is " + newid);
               newids.add(newid);
            } else {
               out.println("attempt to store new image failed!");
            }
         } else if (ctype.indexOf("zip") >= 0) {
            newids.addAll(ImageInserter.addZipByTag
                          (savefile, first_tagid, md.timezone, out, db));
                          
         } else {
            out.println("unrecognized content-type, don't know what to do.");
         }
         if (!savefile.delete()) {
            out.println("whuh-oh!  failed to delete temporary file!");
         }
      }

      if (newids.size() == 0) {
         sendResponse(res, newids, logger);
         return;
      }

      // now stomp them all with any metadata that was supplied with the
      // upload.
      try {
         md.clean();
         // this corny method of rendering the list to "[1, 2, 3]"
         // and then cutting off the [ ] is apparently really what
         // java people do.
         String in_list = newids.toString();
         in_list = in_list.substring(1, in_list.length() - 1);
         // stomp on the image table
         db.createStatement().executeUpdate(newFileImageSql(md, in_list));
         // setting a 'new location' is tricky.  We have to set
         // all the images to the 'parent location', then use one of
         // them to run the logic from the edit page to interpret the
         // random text, then we re-update all the images in this
         // batch with the locationid that EditREST thinks is right.
         if (md.new_location != null) {
            int newloc = EditREST.addFreeformLocation
               (md.new_location, newids.get(0), db);
            db.createStatement().executeUpdate
               ("UPDATE image SET location=" + newloc +
                "WHERE imageid IN (" + in_list + ");");
         }
         // create all the tags that were requested
         PreparedStatement st = db.prepareStatement
            ("INSERT INTO imagetag (image, tag) " +
             "VALUES (?, ?) ON CONFLICT DO NOTHING;"); // look out, postgres
         for (int i : newids) {
            st.setInt(1, i);
            for (int j : md.tags) {
               st.setInt(2, j);
               st.executeUpdate();
            }
         }
      } catch (SQLException e) {
         out.println("*** database error: " + e.getMessage());
         out.println("the data may be screwed up now!");
      }

      // return value is a json object { ids: [1, 2, ..], log: "blah" }
      sendResponse(res, newids, logger);
      return;
   }

   private void sendResponse
   (HttpServletResponse res, ArrayList<Integer> newids, StringWriter logger)
   throws IOException
   {
      JSONObject ret = new JSONObject();
      ret.put("ids", new JSONArray(newids));
      ret.put("log", logger.toString());
      res.setStatus(SC_OK);
      res.setContentType("text/json");
      res.setContentLength(ret.toString().length());
      res.getWriter().write(ret.toString());
      res.getWriter().close();
   }

   private class NewFileMetadata
   {
      public String timestamp;
      public int location;
      public String new_location;
      public String camera;
      public String film;
      public String process;
      public String timezone;
      public String watermark;
      public ArrayList<Integer> tags;
      public ArrayList<FilePart> fileparts;
      public ArrayList<File> savefiles;
      public String error;

      public NewFileMetadata()
      {
         this.tags = new ArrayList<Integer>();
         this.fileparts = new ArrayList<FilePart>();
         this.savefiles = new ArrayList<File>();
      }

      public void clean()
      {
         if ("".equals(this.timestamp)) this.timestamp = null;
         if ("".equals(this.new_location)) this.new_location = null;
         if ("".equals(this.camera)) this.camera = null;
         if ("".equals(this.film)) this.film = null;
         if ("".equals(this.process)) this.process = null;
         if ("".equals(this.timezone)) this.timezone = null;
         if ("".equals(this.watermark)) this.watermark = null;
         if ("".equals(this.error)) this.error = null;
         if (this.location == 0) this.location = 1;
      }
   }

   private String newFileImageSql(NewFileMetadata md, String in_list)
   {
      ArrayList<String> fields = new ArrayList<String>();
      StringBuffer sql = new StringBuffer();
      if (md.timestamp != null) {
         fields.add("ts = TIMESTAMP " +
                    PhotoUtils.quoteString(md.timestamp));
      }
      if (md.timezone != null) {
         fields.add("timezone=" + PhotoUtils.quoteString(md.timezone));
      }
      if (md.location > 0) {
         fields.add("location=" + md.location);
      }
      if (md.camera != null) {
         fields.add("camera=" + PhotoUtils.quoteString(md.camera));
      }
      if (md.film != null) {
         fields.add("film=" + PhotoUtils.quoteString(md.film));
      }
      if (md.process != null) {
         fields.add("process=" + PhotoUtils.quoteString(md.process));
      }
      if (md.watermark != null) {
         fields.add("watermark=" + PhotoUtils.quoteString(md.watermark));
      }
      if (fields.size() > 0) {
         sql.append("UPDATE image SET ");
         sql.append(String.join(",", fields));
         sql.append(" WHERE imageid IN (");
         sql.append(in_list);
         sql.append(");");
      }
      return sql.toString();
   }

   private File saveFilePart(FilePart fp)
   throws IOException
   {
      // note that getNewUploadFile() thinks the first parameter is the
      // userid that owns the file.  we are just going to say "0" meaning
      // "unknown" and it won't care.
      File savefile = PhotoUtils.getNewUploadFile(0, fp.getFileName());
      long len = fp.writeTo(savefile);
      return savefile;
   }
}
