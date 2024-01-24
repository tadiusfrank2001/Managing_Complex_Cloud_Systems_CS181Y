import { getIcon } from "./icons.js";
import { formgrp, selGrp, timegrp,
         TagGrp, PopulateTagGrp } from "./inputs.js";
import { BigImage, CleanedMetadata } from "./display.js";
import { handleSuggestions } from "./suggest.js";
import { perror } from "./nav.js";

const REST_URL = "/rest/edit";

// Exported functions ========================================================

// The only export is EditForm(), which will return a <div> containing
// all the UI elements for the photo detail edit form.  It will also
// send off some ajax to populate the dropdown boxes and do some other
// stuff.
export function EditForm() {

    let ediv = $("<div>");
    // everything editable is enclosed in this <form>, but it is built
    // empty and has no event handlers attached to it.  That will be
    // done by navToImage() after the fact.
    $("<form>", {"id": "frmmetadata"})
        .append($("<input>", {"id": "form_imgid", "type": "hidden"}))
        .append(formgrp("inpcap", "text", "Caption", null, true))
        .append(TagGrp(getIcon("tag"), "tag", handleSingleUpdate))
        .append("<hr/>")
        .append(TagGrp(getIcon("person"), "ppl", handleSingleUpdate))
        .append("<hr/>")
        .append(selGrp("loc", getIcon("location"), "or be more specific"))
        .append(selGrp("wmk", "Watermark", "or create new watermark"))
        .append(timegrp({}))
        .append(formgrp("inptit", "text", "Title"))
        //.append(dblFormGrp({}, "Aperture", "apt", "Shutter", "sht"))
        //.append(dblFormGrp({}, "Camera", "cam", "Focal Length", "foc"))
        //.append(dblFormGrp({}, "Film", "flm", "Process", "pcs"))
        //.append(dblFormGrp({}, "Exposure", "exp", "Metering", "met"))
	.appendTo(ediv);

    $("<div>", {"class": "row my-3"})
        .append($("<div>", {"class": "col-2"}))
        .append($("<div>", {"class": "col-5", "id": "gpsstuff"}))
	.append($("<div>", {"class": "col-5 text-right"})
                .append($("<span>", {"id": "imgflag",
                                     "class": "badge badge-danger mr-3"}))
                .append($("<button>", {"id": "btnprev",
                                       "class": "btn btn-primary mr-3"})
                        .html(getIcon("arrow-left"))
                        .on("click", handleEditPrev))
                .append($("<button>", {"id": "btnnext",
                                       "class": "btn btn-primary mr-3"})
                        .html(getIcon("arrow-right"))
                        .on("click", handleEditNext))
                .append($("<button>", {"id": "btnexf",
                                       "class": "btn btn-danger mr-3"})
                        .text("Reload EXIF")
                        .attr("act", "exf")
                        .on("click", handleSingleUpdate))
		.append($("<button>", {"id": "btndel",
                                       "class": "btn btn-danger"})
			.text("Delete")
                        .attr("act", "del")
			.on("click", handleSingleUpdate)))
	.appendTo(ediv);

    // this going to do some ajaxes
    populateEditForm();
    // we turn on a keydown handler that will clean itself up when it
    // fires and notices that #frmmetadata is gone.
    $(document).off("keydown").on("keydown", handleKeydown);
    return ediv
}

// Subroutines ===============================================================

function populateEditForm() {
    if (!$("#frmmetadata").data("suggestdone")) {
        // detour to fill in the suggestions for watermark and
        // timezone before we proceed.
        $.ajax({"url": "/rest/suggest?wmk=1&tmz=1",
                "type": "GET",
                "success": function(json) {
                    handleSuggestions(json);
                    $("#frmmetadata").data("suggestdone", true);
                    populateEditForm(); // recurse!  But just once.
                },
                "error": perror});
    }  else {
        $.ajax({"url": REST_URL,
	        "data": {"img": $("#imgid").text()},
	        "type": "GET",
	        "success": function(json) { changeImage(json, true); },
                "error": perror});
    }
}

function changeImage(json, img_is_new) {
    $("#frmmetadata").off("change");

    if (img_is_new) {
        document.title = "Edit " + json.id;
        $("#form_imgid").val(json.id);

        // this is corny, but we have to go fishing for how to replace the
        // BigImage.  It isn't good enough to stomp on the src attribute
        // because we need it to come with margins fixed too.
        let imgdiv = $("#content").find("img").parent();
        if (imgdiv.length == 0) {
            console.log("could not find image to replace");
        } else {
            $(imgdiv[0]).html(BigImage(json.id));
        }

        // Put a bunch of stuff that isn't useful to edit in text at
        // the bottom.
        let junk = CleanedMetadata(json);
        let stuff = `${junk.cam} - ${junk.siz} - ${junk.fil}\n` +
            `${junk.exp} - ${junk.met} metering - flash ${junk.fls}\n` +
            `${junk.apt} ${junk.sht} ${junk.foc} ${junk.flm} ${junk.pcs}`;
        if (junk.gps) {
            stuff += `\nGPS ${junk.gps}`;
        }
        let g = $("#gpsstuff");
        g.text(stuff);
        g.html(g.html().replace(/\n/g, "<br/>"));
        $("#inpcap").focus();
    }

    for (let i of ["cap", "rot", "wmk", "ts", "tz", "tit"]) {
        if (img_is_new || json[i] !== undefined) $("#inp" + i).val(json[i]);
    }
    setFlag(json);
    if (json.tag !== undefined) { PopulateTagGrp(json, "tag"); }
    if (json.ppl !== undefined) { PopulateTagGrp(json, "ppl"); }
    if (json.loc !== undefined) { handleSuggestions({"loc": json.loc}); }
    $("#frewmk").val("");
    $("#fretz").val("");
    $("#frmmetadata").on("change", handleSingleUpdate);
}

function updateComplete(json, status, xhr) {
    // attempt to detect if the image just changed from "new" to "not
    // new" status, and if so, decrease the number in the notification
    // bar.  This is a hack; the always-correct way would be to
    // re-fetch the new count, but that is an unnecessary server trip.
    if ($("#imgflag").text() == "New" && !json.new) {
        let ncount = $("#newbtn span");
        ncount.text(ncount.text() - 1);
    }

    // the server tries to return only elements that changed.  So
    // changeImage() needs to be nondestructive when it looks for
    // something that is undefined in the json.
    changeImage(json, false);
}

function setFlag(json) {
    if (json.del) { $("#imgflag").text("Deleted"); }
    else if (json.new) { $("#imgflag").text("New"); }
    else { $("#imgflag").text(""); }
}

// Event handlers ============================================================

function handleSingleUpdate(event) {
    let post_data = { "img": $("#form_imgid").attr("value") };

    // we hackily interpret the names of the input elements to figure
    // out how to POST their changes.
    let target_type = event.target.id.substring(0, 3);
    if (target_type == "inp") {
	post_data[event.target.id.substring(3)] = event.target.value;
    } else if (target_type == "fre") {
	post_data[event.target.id.substring(3) + "_fre"] = event.target.value;
        event.target.value = "";
    } else if (target_type == "tag") {
        let parts = event.target.id.split("_");
        if ($(event.target).hasClass("badge-info")) {
            post_data[parts[1] + "_del"] = parts[2];
        } else {
            post_data[parts[1]] = parts[2];
        }
    } else if (target_type == "btn") {
        post_data[$(event.target).attr("act")] = true;
    }

    $.ajax({"url": REST_URL,
	    "data": post_data,
	    "type": "POST",
	    "success": updateComplete,
	    "error": perror});
    return false;
}

function handleEditPrev(event) {
    let imgid_list = $(window).data("imgid_list");
    if (imgid_list === undefined) return false;
    let curid = $("#form_imgid").attr("value");
    let filter = function(x) { return x == curid; }; 
    let curidx = imgid_list.findIndex(filter);
    if (curidx > 0) {
	$("#imgid").text(imgid_list[curidx - 1]);
        populateEditForm();
    }    
}

function handleEditNext(event) {
    let imgid_list = $(window).data("imgid_list");
    if (imgid_list === undefined) return false;
    let curid = $("#form_imgid").attr("value");
    let filter = function(x) { return x == curid; }; 
    let curidx = imgid_list.findIndex(filter);
    if (curidx < imgid_list.length - 1) {
	$("#imgid").text(imgid_list[curidx + 1]);
        populateEditForm();
    }    
}

function handleKeydown(event) {
    // this event handler self destructs if the form is no longer there
    if ($("#frmmetadata").length == 0) {
        $(document).off("keydown");
        return;
    }
    if (event.ctrlKey && event.key == "ArrowLeft") handleEditPrev();
    if (event.ctrlKey && event.key == "ArrowRight") handleEditNext();
}
