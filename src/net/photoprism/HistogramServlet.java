package net.photoprism;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet for drawing a histogram.  All access is by doGet(), since it is likely
 * that this servlet will be invoked by an img src property.
 *
 * @author Michael A. Dickerson
 * @version 20051014
 *
 */

public class HistogramServlet extends PhotoServlet
{
  private static final long serialVersionUID = 1L;

  /**
   * prepare our sql instance variable at init (st will be initialized
   * on the first call to executeQuery())
   *
   * @param config ServletConfig
   * @exception ServletException if super throws it
   */

  public void init(ServletConfig config) throws ServletException
  {
    super.init(config);
    sql = new StringBuffer();
  }

  /**
   * doGet() expects only one argument, id, and always returns a 256x100
   * pixel PNG with no transparency (for now).  Note that this servlet
   * does not even check whether you own the specified image.  It does
   * not seem like much of a security leak to let random people look up
   * the histograms for images they can't see.
   *
   * @param req HttpServletRequest
   * @param res HttpServletResponse
   */

  public void doGet(HttpServletRequest req, HttpServletResponse res)
  {
    timeCheckpoint(null);

    int imgID = 0;
    try {
      imgID = Integer.parseInt(req.getParameter("id"));
    } catch (NumberFormatException e) {
      log("non-numeric id argument: " + req.getParameter("id"));
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    String fname = null;
    int period = 4;

    // ask pkeep where the image is
    try {
      String url = PhotoUtils.askPkeep(imgID, 640, "");
      if (url.startsWith("file://")) fname = url.substring(7);
    } catch (IOException e) {
      log("weird pkeep exception: " + e);
      fname = null;
    }

    // If we still have nothing, try to get the original image
    // file.  We should really probably just give up at this
    // point.
    if (fname == null) {
      sql.setLength(0);
      sql.append("SELECT imagefile FROM image WHERE imageid = ");
      sql.append(imgID);
      sql.append(';');
      executeQuery();
      try {
        rs.next();
        fname = rs.getString(1);
        rs.close();
      } catch (SQLException e) {
        log("database exception: " + e);
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return;
      }
      // if we have to load the full size image, at least downsample
      // for the histogram to save a little time
      period = 4; 
      timeCheckpoint("database lookup finished");
    }

    // acquire image (we'll try pure jai this time)

    PlanarImage src = (PlanarImage)JAI.create("fileload", fname);
    timeCheckpoint("loaded image");
    PlanarImage hist = DrawHistogram.drawHistogram(src, 256, 100, period);
    timeCheckpoint("prepared histogram");

    if (debug) 
      log("histogram is " + hist.getWidth() + "x" + hist.getHeight() + "px");

    try {
      res.setStatus(HttpServletResponse.SC_OK);
      res.setContentType("image/png");
      //BufferedOutputStream out = new BufferedOutputStream(res.getOutputStream());
      if(!ImageIO.write(hist, "png", res.getOutputStream())) {
        log("ImageIO.write returned false, probably nothing sent.");
      }
      //out.close();
    } catch (IOException e) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      log("while sending histogram: " + e);
    }

  }

  /**
   * these are all instance variables, so why not a method to do it
   */

  private void executeQuery()
  {
    try {
      if (st == null) st = db.createStatement();
      rs = st.executeQuery(sql.toString());
    } catch (SQLException e) {
      log("query failed: " + sql.toString());
      log("exception was: " + e);
    }
  }

  private ResultSet rs;
  private StringBuffer sql;
  private Statement st;
}
