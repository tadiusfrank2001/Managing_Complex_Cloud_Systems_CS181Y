package net.photoprism;

import java.awt.Point;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.Histogram;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterFactory;
import javax.media.jai.TiledImage;

/**
 * A class to draw image histograms as PNG graphics.  Can be executed by itself,
 * also exposes drawHistogram() methods for use by other java programs.
 * 
 * <p>Copyright &copy; 2005-06 Michael A. Dickerson.  Modification and use
 * are permitted under the terms of the GNU General Public License version 2,
 * and possibly another license as specified in the package where you found
 * this file.</p>
 *
 * @author Michael A. Dickerson <mikey@singingtree.com>
 * @version 20051012
 */


public class DrawHistogram
{

    /**
     * run this class by itself to create a 250x200 pixel histogram with
     * a sampling period of 4, probably most useful for testing.
     */

    public static void main(String args[])
    {
	if (args.length != 2) {
	    System.err.println("Usage: DrawHistogram in.jpg out.png");
	    System.exit(1);
	}

	System.out.println("Loading " + args[0]);
	TiledImage src = new TiledImage(JAI.create("fileload", args[0]), true);
	System.out.println("Computing histogram");
	PlanarImage hist = drawHistogram(src, 250, 200, 4);
	System.out.println("Writing to " + args[1]);
	//ImageIO.write(hist, "jpeg", new File(args[1]));
	ParameterBlock pb = new ParameterBlock();
	pb.addSource(hist);
	pb.add(args[1]);
	pb.add("PNG");
	JAI.create("filestore", pb);
	return;
    }

    /**
     * convenience method that assumes you want to use a sample period of 1
     * (i.e. count every pixel)
     *
     * @param src PlanarImage in which to count pixels
     * @param w width (pixels) of histogram image to create
     * @param h height (pixels) of histogram image to create
     * @return PlanarImage representing histogram
     */

    public static PlanarImage drawHistogram(PlanarImage src, int w, int h)
    {
	return drawHistogram(src, w, h, 1);
    }

    /**
     * Method to create human-readable histograms as PlanarImages, from
     * arbitrary source images.  The R, G, B color bands will be drawn
     * independently on a black background, with overlapping areas
     * OR-ed together (as histograms are usually displayed).  Setting
     * a sample period N > 1 allows you to trade (a little) accuracy for
     * (a lot) of time by counting only every Nth pixel in the source
     * image.
     *
     * We are able to use JAI to do some of the heavy lifting and compute
     * the initial statistics, but we have to do all the ugly bit arithmetic
     * to convert the statistics into a raster image for display.
     *
     * @param src PlanarImage in which to count pixels
     * @param w width (pixels) of histogram image to create
     * @param h height (pixels) of histogram image to create
     * @param period sampling period (vertical and horizontal)
     * @return PlanarImage representing histogram
     */

    public static PlanarImage drawHistogram(PlanarImage src, int w, int h, int period)
    {
	// use jai histogram operation to count the pixels
	int numBands = src.getNumBands();
	int[] bins = new int[numBands];
	int i, j;
	for (i = numBands - 1; i >= 0; --i) bins[i] = w;
	ParameterBlock pb = new ParameterBlock();
	pb.addSource(src);
	pb.add(null);    // region of interest
	pb.add(period);  // x sample period
	pb.add(period);  // y sample period
	pb.add(bins);    // number of bins per band
	pb.add(null);    // lowest value to count (per band)
	pb.add(null);    // highest value to count (per band)
	Histogram hist = (Histogram)(JAI.create("histogram", pb).getProperty("histogram"));

	// find largest number of pixels in a bin, in order to set vertical
	// scale
	int band;
	i = 0;
	for (band = numBands - 1; band >= 0; --band) {
	    bins = hist.getBins(band);
	    for (j = bins.length - 1; j >= 0; --j) {
		if (bins[j] > i) i = bins[j];
	    }
	}
	float max = (float)i;

	if (debug) System.out.println("found max bin count: " + max);

	int[][] pixels = new int[w][h];
	int top;
	for (band = numBands - 1; band >= 0; --band) {
	    bins = hist.getBins(band);
	    // 0xff0000 on red band, 0xff00 on green, 0xff on blue
	    int pixelVal = 0xff << (band * 8);
	    if (debug) System.out.println("pixelVal for band " + band + " is " + pixelVal);
	    for (j = bins.length - 1; j >= 0; --j) {
		top = h - (int)(bins[j] / max * h);
		for (i = h - 1; i > top; --i) {
		    pixels[j][i] |= pixelVal;
		}
	    }
	}

	/*
	// create a writable image with constant RGB value 0,0,0
	ParameterBlock pb = new ParameterBlock();
	pb.add((float) w);
	pb.add((float) h);
	Byte[] vals = new Byte[3];
	for (int i = 2; i >= 0; --i) vals[i] = new Byte((byte)0);
	pb.add(vals);
	TiledImage img = new TiledImage(JAI.create("constant", pb), true);
	*/
	
	// the process of making an array of ints into an image was taken
	// from the example at:
	// https://jaistuff.dev.java.net/Code/data/CreateRGBImage.java

	if (debug) System.out.println("packing " + w * h * 3 + " bytes");

	byte[] pixelsFlattened = new byte[w * h * 3];
	int pixel;
	top = (w * h * 3) - 1;
	for (i = h - 1; i >= 0; --i) {
	    for (j = w - 1; j >= 0; --j) {
		pixel = pixels[j][i];
		pixelsFlattened[top--] = (byte)(pixel & 0xff);
		pixelsFlattened[top--] = (byte)((pixel >> 8) & 0xff);
		pixelsFlattened[top--] = (byte)((pixel >> 16) & 0xff);
	    }
	}

	DataBufferByte buff = new DataBufferByte(pixelsFlattened, w * h * 3);
	SampleModel samp = RasterFactory.createPixelInterleavedSampleModel
	    (DataBuffer.TYPE_BYTE, w, h, 3);
	ColorModel color = PlanarImage.createColorModel(samp);
	Raster raster = RasterFactory.createWritableRaster
	    (samp, buff, new Point(0, 0));
	TiledImage img = new TiledImage(0, 0, w, h, 0, 0, samp, color);
	img.setData(raster);

	/*
	// Print raw numbers to stdout.
	for (int i=0; i< hist.getNumBins()[0]; i++){
	    System.out.println(hist.getBinSize(0, i) + " " +
			       hist.getBinSize(1, i) + " " +
			       hist.getBinSize(2, i));
	}
	*/	
	return img;
    }

    /**
     * if debug == true, some information will be printed to stderr as
     * histograms are computed.  Note that when running under tomcat,
     * stderr shows up in catalina_2005-xx-xx.log (in my configuration
     * at least).
     */

    private static boolean debug = false;

}
