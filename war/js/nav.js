import { getUploadPage } from "./upload.js";
import { getTokenPage } from "./tokens.js";
import { GetPhotos } from "./display.js";

export function navToHash()
{
    //console.log("navToHash: " + window.location.hash);
    $(window).off("scroll");
    $(document).off("keydown");
    // note that charAt(0) is always '#'
    switch (window.location.hash.charAt(1)) {
    case 't':
        setButtonState("");
        getTokenPage();
        break;
    case 'u':
        setButtonState("");
        getUploadPage();
        break;
    case 'd':
        setButtonState("");
        GetPhotos();
        break;
    }
}

function setButtonState(what)
{
    if (what === "edit") {
	$("#prevbtn").removeClass("d-none");
	$("#nextbtn").removeClass("d-none");
	$("#editbtn").addClass("bg-primary")
            .removeClass("disabled")
	    .off("click")
	    .on("click", function() {
                window.location.hash = window.last_location;
            });
    } else {
	$("#prevbtn").addClass("d-none");
	$("#nextbtn").addClass("d-none");
	$("#editbtn").removeClass("bg-primary")
            .addClass("disabled")
	    .off("click")
	    .on("click", function() {
                window.last_location = window.location.hash;
                window.location.hash = "e";
            });
    }
}


export function updateNotifications()
{
    $.ajax({"url": "/servlet/browserest",
	    "data": {"mode": "n"},
	    "type": "GET",
	    "error": function(xhr, status, err) {
		perror(`ajax status: ${status}<br/>error: ${err}`); },
	    "success": notificationCallback
	   });
}

function notificationCallback(j)
{
    if (j.n > 0) {
        $("#newbtn").empty()
	    .removeClass("d-none")
	    .text("New")
	    .append($("<span>", {"class": "badge badge-light ml-2"}).text(j.n))
	    .off("click.notif")
	    .on("click.notif", function() {
                window.location.hash = "#ddn";
            });
    } else {
	$("#newbtn").addClass("d-none").off("click.notif");
    }
}


export function pageBreak(text, subtext) {
    if (subtext === undefined) {
        return pageBreakObj(`<h4>${text}</h4>`);
    } else {
        return pageBreakObj(`<h4>${text}</h4><p>${subtext}</p>`);
    }
}


export function pageBreakObj(obj) {
    return $("<div>", {"class": "alert alert-primary py-2 my-3"})
        .append(obj);
}


// perror is designed to fit the "error" prototype from $.ajax().  It
// doesn't care about the xhr argument, so you can just set xhr=null
// and use it from other places.

export function perror(xhr, status, err) {
    console.log(`whuh-oh: ${status} - ${err}`);
    $("#content").empty();
    $("<div class=\"alert alert-danger\">")
        .html("<p>Nuts!  A server request failed.</p>" +
              `<p>Status: ${status}<br />` +
              `Message: ${err || xhr.responseText}</p>` +
              "<p>The browser developer tools may have more information.</p>")
	.appendTo($("#content"));
}
