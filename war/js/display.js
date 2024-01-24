import { EditForm } from "./edit.js";
import { RenderBatchEditForm } from "./edit_batch.js";
import { getIcon } from "./icons.js";
import { updateNotifications, pageBreak, perror } from "./nav.js";
import { renderTag } from "./tokens.js";

// Exported functions ========================================================

// using the current computed layout and the currently cached photo
// data, return a big image as an <img> object.

export function BigImage(id) {
    let pic = $(window).data("imgid_map")[id];
    if (pic === undefined) {
        console.log("missing image data for " + id);
        return;
    }
    return fitImageToBbox(pic,
                          $(window).data("imgbbox"),
                          $(window).data("imgrendersize"))
        .addClass("pp-bigimage");
}

// this function submits a bag of parameters to the REST "browse"
// endpoint, and expects a json response like this:
// { title: something,
//   params: same params back again,
//   images: [ { id: image_id,
//               count: a number to display with the text
//               text: "what to display under the image",
//               act: { what to do if you click } }, ... ]
// }

export function GetPhotos() {
    // we read window.location.hash for the complete specification of
    // what to fetch.

    // GetPhotos should only be running if the fragment starts with #d.
    if (!window.location.hash.startsWith("#d")) {
        console.log("GetPhotos is running with wrong hash " +
                    window.location.hash);
        return;
    }

    let get_data = {"mode": window.location.hash.charAt(2)};
    switch (get_data.mode) {
    case "l":
        get_data.id = window.location.hash.substr(3);
        setMenuState("jump", "location");
        break;
    case "p":
        setMenuState("jump", "person");
        break;
    case "r":
        setMenuState("jump", "recent");
        break;
    case "m":
        setMenuState("jump", "month");
        break;
    case "d":
        let submode = window.location.hash.charAt(3);
        let arg = window.location.hash.substr(4);
        switch (submode) {
        case "d":
            get_data.date = arg;
            break;
        case "m":
            get_data.month = arg;
            break;
        case "p":
            get_data.person = arg;
            break;
        case "t":
            get_data.tag = arg;
            break;
        case "n":
            get_data.new = 1;
            break;
        default:
            console.log("bad submode of d fragment: " + window.location.hash);
            return;
        }
        break;
    }

    // create mode menu if it doesn't exist.
    if ($("#menumode-top").length == 0) {
        $(MODE_MENU).insertAfter($("#menujump"));
        $("#menumode-drop").on("click", handleModeChange);
    }

    $.ajax({"url": "/servlet/browserest",
	    "data": get_data,
	    "type": "GET",
	    "error": function(xhr, status, err) {
		if (xhr.status == 403) {
		    // send you to token (login) page if you were
		    // rejected outright by the server
                    window.location.hash = "t";
		} else {
		    perror(xhr, status, err);
		}
	    },
	    "success": loadPhotos });
    return false;
}

// a real 35mm slide is a 24x36mm transparency centered in a 50mm
// square mount.  (it is supposed to be 2in=50.8mm, but actual slides
// seem to usually be 49-50mm on a side.)
//
//   +----------------+  -+
//   |     13mm       |   |
//   |  +----------+  |   |
//   |  |          |  |   |
//   | 7|  24x36mm |7 |   | 50mm
//   |  |          |  |   |
//   |  +----------+  |   |
//   |      13mm      |   |
//   +----------------+  -+
export function Slide(pic) {
    // create classes for ourselves unless they are already there
    if ($("#pp-style-slide").length == 0) {
        $("<style>", {id: "pp-style-slide"}).html(`
          div.pp-slide {
            background-color: white;
            border-style: outset;
            padding: ${Math.floor((7/50) * 192)}px;
            width: 192px;
            height: 192px;
          }
          div.pp-slide img {
            border-style: inset;
          }`).appendTo($("body"));
    }

    let img = fitImageToBbox(pic, Math.floor((36/50) * 192), 350);
    let slide = $("<div>", {"id": "slide" + pic.id, "class": "pp-slide"})
        /*
        .css("background-color", "white")
        .css("border-style", "outset")
        .css("padding", Math.floor((7/50) * 192))
        .width(192)
        .height(192)
        */
        .append(img)
        .append($("<div>")
                .css("text-align", "right")
                .text(pic.id));
    return $("<div>", {"class": "d-inline-block m-4"})
        .append(slide);
}

// if you have a bag of metadata fresh from the server, there are some
// things you probably want to fix before displaying it.  This does it
// non-destructively by returning a copy after modifications, saving
// you from weird bugs later.
export function CleanedMetadata(m) {
    let cleaned = {};
    for (let [k, v] of Object.entries(m)) {
        switch(k) {
        case "sht":
            // sht usually looks like '0.0063 s (1/160)' and we only
            // keep the '1/160' part.
            cleaned.sht = v.replace(/.*\(([0-9/]+)\)/, '$1');
            break;
        case "foc":
            // foc usually looks like '24.0mm (35mm equivalent
            // 1150mm)' except the '35mm equivalent' part is now
            // usually wrong.  I don't know why; it used to make sense
            // from jhead.
            cleaned.foc = v.replace(/^([0-9\.m]+).*/, '$1');
            break;
        case "fls":
            // for booleans
            cleaned[k] = ( v ? "yes" : "no" );
            break;
        default:
            cleaned[k] = v;
            break;
        }
    }
    // stick in a couple of synthetics that you can use if you find them
    cleaned.tsz = `${m.ts} ${m.tz}`;
    if (m.lat || m.lon || m.alt) {
        // cool we don't have printf or anything like it
        let NS = "" + Math.round(Math.abs(m.lat) * 10000) / 10000;
        NS += ( m.lat < 0 ? " S" : " N" );
        let EW = "" + Math.round(Math.abs(m.lon) * 10000) / 10000;
        EW += ( m.lon < 0 ? " W" : " E" );
        cleaned.gps = `(${NS}, ${EW}) Altitude ${m.alt}m`;
    }
    let MP = "" + Math.round(m.wid * m.hgt / 100000) / 10;
    cleaned.siz = `${MP}MP ${m.wid}x${m.hgt}`;
    return cleaned;
}

// Subroutines ===============================================================

const MODE_MENU = `
  <div class="nav-item dropdown">
    <a id="menumode-top", class="nav-link dropdown-toggle" href="#"
       data-toggle="dropdown">roll</a>
    <div id="menumode-drop", class="dropdown-menu bg-dark text-white">
      <a class="dropdown-item">roll</a>
      <a class="dropdown-item">slides</a>
      <a id="editmode" class="dropdown-item disabled">edit</a>
      <a id="batchmode" class="dropdown-item disabled">edit batch</a>
    </div>
  </div>
`;

// Copy the received data to $(window).data(), then let renderPhotos()
// clear and recreate the page.
function loadPhotos(json) {
    if (!json || !json.images) {
	perror(null, "200", "json response contained no images.")
	return;
    }

    $(window).data("imgid_list", [])
        .data("imgid_map", {})
        .data("photo_list", [])
        .data("title", json.title);
    for (let pic of json.images) {
        $(window).data("photo_list").push(pic);
        if (pic.hdr === undefined && pic.count === undefined) {
            $(window).data("imgid_list").push(pic.id);
            $(window).data("imgid_map")[pic.id] = pic;
        }
    }
    renderPhotos();
}

// Clear and recreate the main scrolling part of the page.  Assumes
// $(document).data() already contains photo_list (see loadPhotos()).
// Understands multiple styles and remembers the last one that was
// used if none is specified.  Also sets document.title and start the
// scroll-spy handler.
function renderPhotos(style) {

    calculateLayout();
    let wd = $(window).data();

    // use the last non-edit style if none was specified
    if (!style) {
        style = (wd.viewstyle || "roll");
    } else if (style.substring(0, 4) != "edit")  {
        wd.viewstyle = style;
    }

    // set page title and show where you are
    document.title = wd.title;
    setMenuState("mode", style);

    // rewrite the content part of the page
    let cdiv = $("#content").empty();

    switch (style) {
    case "edit":
        // unlike the other styles, this renders only one picture at a
        // time.
        let id = $("#imgid").text();
        // the <div> around BigImage is important because the edit
        // form is going to assume it can replace the contents of
        // bigimage.parent()
        cdiv.append($("<div>").append(BigImage(id)));
        cdiv.append(EditForm());
        break;
    case "edit batch":
        RenderBatchEditForm(cdiv);
        break;
    case "roll":
    case "slides":
        for (let pic of wd.photo_list) {
	    if (pic.hdr) {
	        // this is a page break
                cdiv.append(pageBreak(pic.hdr));
	    } else if (pic.count) {
	        // this is a navigational thumbnail
	        $("<div>", {"class": "d-inline-block m-4 p-2 border"})
		    .append(fitImageToBbox(pic, 192, 192))
		    .append(`<p>${pic.text} // ${pic.count}</p>`)
                    .on("click", function() {
                        window.location.hash = pic.act;
                    })
		    .appendTo(cdiv);
	    } else {
	        // this is a photo for display
                if (style == "roll") {
	            cdiv.append(renderDetailPhoto(pic));
                } else if (style == "slides") {
                    cdiv.append(Slide(pic));
                }
	    }
        }
        break;
    }

    // check for new notifications
    updateNotifications();

    // set the imageID label and turn on the scroll-spyer
    reallyHandleScroll();
    $(window).off("scroll").on("scroll", maybeHandleScroll);
}

// the original and default layout, which just makes the images as big
// as possible.
function renderDetailPhoto(pic) {

    let imgdiv = $("<div>");

    // if the picture had a "title", render it in a header-like box.
    // This lets you insert title-card-like breaks into a stream.
    if (pic.tit) {
        imgdiv.append(pageBreak(pic.tit));
    }

    imgdiv.append(BigImage(pic.id));

    if ($(window).data("orientation") === "tall") {
        // here's what we are drawing
        // +-------------- 7 --------------+--- 5 ----+
        // | caption                       | tag tag  |
        // | location                      | date     |
        // | person, person, person        | camera   |
        // +-------------------------------+----------+
        let left = $("<div>", {"class": "col-7"})
            .append($("<span>").text(pic.cap))
            .append("<br/>")
            .append(mapPin(pic))
            .append($("<a>", {"href": `#dl${pic.loc[0].id}`})
                    .text(pic.loc[0].txt))
            .append("<br/>");
        for (let p of pic.ppl || []) {
            left.append($("<a>", {"href": `#ddp${p.id}`}).text(p.txt));
            left.append(" ");
        }

        let right = $("<div>", {"class": "col-5"});
        for (let t of pic.tag || []) { right.append(renderTag(t.txt)); }
        right.append("<br/>")
            .append($("<a>", {"href": `#ddd${pic.dat}`}).text(pic.dat))
            .append("<br/>")
            .append($("<span>")
                    .text(pic.cam)
                    .on("click", togglePopover));

        $("<div>", {"class": "row"})
            .append(left)
            .append(right)
            .appendTo(imgdiv);

    } else {
        // orientation === "wide": put metadata in a column after the
        // picture
        let dat = $("<div>", {"class": "pp-data"});
        let md = CleanedMetadata(pic);
        dat.append(md.cap + "<hr/>");
        for (let t of md.tag) { dat.append(renderTag(t.txt)); }
        dat.append("<br/>")
            .append($("<a>", {"href": `#ddd${md.dat}`}).text(md.dat))
            .append("<br/>")
            .append(mapPin(md))
            .append($("<a>", {"href": `#dl${md.loc[0].id}`})
                   .text(md.loc[0].txt))
            .append("<hr/>");
        for (let p of md.ppl || []) {
            dat.append($("<a>", {"href": `#ddp${p.id}`}).text(p.txt));
            dat.append("<br/>");
        }
        dat.append("<hr/>");
        let tbl = $("<table>", {"class": "table table-sm table-striped"});
        let tbody = $("<tbody>");
        for (let r of METADATA_FIELDS) {
            if (md[r[0]]) {
                $("<tr>")
                    .append($("<td>").text(r[1]))
                    .append($("<td>").text(md[r[0]] || ""))
                    .appendTo(tbody);
            }
        }
        tbody.appendTo(tbl);
        tbl.appendTo(dat);
        dat.appendTo(imgdiv);
    }
    return imgdiv;
}

// for when you need the size of "one rem" in pixels.
function oneRem()
{
    return parseInt($("html").css("font-size"));
}

// Examine the browser window size and density and save four
// parameters "imgbbox", "imgrendersize", "orientation", and
// "datasize" in $(window).data().
//
// TODO: Communicate via the stapled-on CSS classes where possible.
//
function calculateLayout()
{
    let w = $(window);
    let cdiv = $("#content");

    let dynStyle = {};
    // hack: stomp the top and bottom margins to be small on BigImage
    // in roll mode; don't need them to fit in a square.  This
    // overrides the margins that fitImgToBbox() will calculate for
    // us.
    dynStyle["img.pp-bigimage"] = {"margin-bottom": "1rem !important",
                                   "margin-top": "1rem !important"};

    // this is crazy, but the width of everything except
    // window.outerWidth changes when the vertical scroll bar appears
    // or disappears, which makes a mess of everything.  So we have to
    // infer the size of the scroll bar when we get a peek at it (we
    // can't detect it at all if there isn't presently a scroll bar on
    // the screen) and kludge this calculation back together.
    let content_x = cdiv.width();
    if (w.outerWidth() != w.width()) {
        // the scroll bar is currently visible: cdiv.width() is
        // correct, and save this value for later
        w.data("scrollbarwidth", w.outerWidth() - w.width());
    } else {
        // the scroll bar is not visible, but we want to leave a space
        // for it if we can.  If we have never seen the correct width,
        // we take a guess that matches my chromium.
        content_x -= (w.data("scrollbarwidth") || 14);
    }
    let content_y = w.height() - $("#topbar").height();
    if (content_x > content_y) {
        // "wide" is hard, because if you are off by one pixel it will
        // push the (long, tall) sidebar div onto a new row.
        w.data("orientation", "wide");
        let dataw = Math.floor(Math.max(144, content_x / 5)) - oneRem();
        w.data("imgbbox",
               Math.floor(Math.min(content_y - oneRem(),
                                   content_x - dataw - oneRem() - 1)));
        dynStyle["div.pp-data"] = {"margin-left": "1rem",
                                   "margin-top": "1rem",
                                   "width": dataw + "px",
                                   "vertical-align": "top",
                                   "display": "inline-block"};
    } else {
        // "tall" is easy, because the image and data divs will just
        // stack vertically and be scrollable.
        w.data("orientation", "tall");
        w.data("imgbbox", Math.floor(Math.min(content_x, content_y - 192)));
    }
    // we have to correct by window.devicePixelRatio to discover the
    // number of actual device pixels in img_bbox (which is measured
    // in HTML5/CSS pretend pixels).  We want to send the smallest
    // size that has at least as many pixels as the physical device.
    // Otherwise it looks crappy.
    let img_pixels = w.data("imgbbox") * window.devicePixelRatio;
    w.data("imgrendersize", 1920);
    for (let s of [1440, 1024, 640, 350]) {
        if (img_pixels < s) { w.data("imgrendersize", s); }
    }

    // this is one list comprehension in python
    let newhtml = "";
    for (let [k, v] of Object.entries(dynStyle)) {
        let vals = [];
        for (let [k2, v2] of Object.entries(v)) {
            vals.push(`${k2}: ${v2};`);
        }
        newhtml += `${k} { ${vals.join(" ")} }\n`;
    }
    if ($("#pp-style").length == 0) {
        $("body").append($("<style>", {id: "pp-style"}));
    }
    $("#pp-style").html(newhtml);
}

// Knowing the (square) bounding box size that you want to fit an
// image in, this will create an image tag with computed height,
// width, and left/right/top/bottom margins.
//
// This takes a lot of manual work because you have to force-override
// the way HTML5/CSS3 will "correct" a hidpi display by using the fake
// pixel unit where (it thinks) 96 pixels=1 inch.  This has the effect
// of software-stretching images to fill out the hardware pixels.
// This looks, very unsurprisingly, like shit.

function fitImageToBbox(pic, bbox, rendersize) {
    let img = $("<img>", {"id": "img" + pic.id})
        .data("imageid", pic.id)
        .data("canedit", pic.ed)
        .data("nativeh", pic.hgt)
        .data("nativew", pic.wid);
    if (pic.wid > pic.hgt) {
        let render_height = Math.floor(pic.hgt * (bbox / pic.wid));
        img.width(bbox)
            .height(render_height);
        let margin = (bbox - render_height) / 2;
        if (margin > 0) {
            img.css("margin-top", Math.floor(margin))
                .css("margin-bottom", Math.floor(margin));
        }
    } else {
        let render_width = Math.floor(pic.wid * (bbox / pic.hgt));
        img.height(bbox)
            .width(render_width);
        let margin = (bbox - render_width) / 2;
        if (margin > 0) {
            img.css("margin-left", Math.floor(margin))
                .css("margin-right", Math.floor(margin))
        }
    }
    img.attr("src", `/img/${rendersize}/${pic.id}.jpg`);
    return img;
}

function mapPin(pic) {
    if (pic.lat && pic.lon) {
        let url = "https://www.google.com/maps/search/?api=1&query="
            + encodeURIComponent(`${pic.lat},${pic.lon}`);
        return $("<a>", {"class": "mr-2", "href": url, "target": "map"})
            .html(getIcon("location"));
    }
}

function setMenuState(which, state) {
    $(`#menu${which}-top`).text(state);
    for (let i of $(`#menu${which}-drop`).children()) {
        if (i.text == state) {
            $(i).addClass("active");
        } else {
            $(i).removeClass("active");
        }
    }
}

// Event handlers ============================================================

// handling the scroll event is not really complicated, but it looks
// that way with all the boilerplate that is necessary to throttle the
// handler down to only run once every 100-200ms.  Otherwise you get
// ~20 per second.

let lastScrollEvent = 0;
let scrollTimeout;
function maybeHandleScroll() {
    let now = new Date().getTime();
    if (!scrollTimeout) {
	if (now > lastScrollEvent + 300) {
	    reallyHandleScroll();
	    lastScrollEvent = now;
	}
	scrollTimeout = setTimeout(function() {
	    scrollTimeout = null;
	    lastScrollEvent = new Date().getTime();
	    reallyHandleScroll();
	}, 100);
    }
}

function reallyHandleScroll() {
    let lbl = $("#imgid");
    if ($(window).data("imgid_list") === undefined) {
	lbl.text("");
	return false;
    }
    for (let i of $(window).data("imgid_list")) {
        // real javascript, scary
	let el = document.getElementById("img" + i);
	if (!el) {
	    // this scroll handler self-destructs if it can't find
	    // the img element it needs.
	    $(window).off("scroll");
	    return false;
	}
	if (el.getBoundingClientRect().y > -80) {
            if (lbl.text() != i) {
	        lbl.text(i);
	        if ($(el).data("canedit")) {
                    $("#editmode").removeClass("disabled");
                    $("#batchmode").removeClass("disabled");
	        } else {
                    $("#editmode").addClass("disabled");
                    $("#batchmode").addClass("disabled");
	        }
                updatePopover();
            }
	    return false;
	}
    }
}

function togglePopover(e) {
    let popup = $("#popover");
    if (popup.length === 0) {
        $("<div>", {"id": "popover", "class": "fixed-bottom"})
            .css("background-color", $("body").css("background-color"))
            .css("padding", 15)
            .appendTo($("#content"));
        updatePopover();
    } else {
        popup.remove();
    }
    return false;
}

let METADATA_FIELDS = [["cam", "camera"], ["fil", "origfile"],
                       ["apt", "aperture"], ["sht", "shutter"],
                       ["flm", "film"], ["pcs", "process"],
                       ["exp", "exposure"], ["met", "metering"],
                       ["foc", "focallen"], ["fls", "flash"],
                       ["tsz", "timestamp"], ["siz", "size"],
                       ["gps", "gps"]];

function updatePopover() {
    let popup = $("#popover");
    if (popup === undefined) return;

    let md = CleanedMetadata($(window).data("imgid_map")[$("#imgid").text()]);
    let tbody = $("<tbody>");
    for (let count = 0; count < METADATA_FIELDS.length; count += 2) {
        let f = METADATA_FIELDS[count];
        let row = $("<tr>");
        row.append($("<td>").text(f[1]))
            .append($("<td>").text(md[f[0]] || ""));
        if (METADATA_FIELDS[count + 1] !== undefined) {
            f = METADATA_FIELDS[count + 1];
            row.append($("<td>").text(f[1]))
                .append($("<td>").text(md[f[0]] || ""));
        }
        tbody.append(row);
    }
    popup.empty()
        .append($("<table>", {"class": "table table-sm table-striped"})
                .append(tbody));
}

function handleModeChange(e) {
    let newmode = $(e.target).text();
    renderPhotos(newmode);
}

