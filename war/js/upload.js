import { updateNotifications } from "./nav.js";
import { getIcon } from "./icons.js";
import { formgrp, selGrp, timegrp, headerrow } from "./inputs.js";
import { handleSuggestions } from "./suggest.js";

export function getUploadPage(event) {
    let cdiv = $("#content");
    let parent = cdiv.parent();
    cdiv.detach().empty();

    let form = $("<form>");

    $("<div>", {"class": "row my-3"})
	.append($("<div>", {"class": "col-2 text-right"}).text("File"))
	.append($("<div>", {"class": "col-10 custom-file"})
		.append($("<input>", {"id": "uploadfile",
				      "class": "custom-file-input",
				      "type": "file"}))
		.append($("<label>", {"class": "custom-file-label mx-3",
				      "for": "uploadfile"})
			.text("choose something")))
	.appendTo(form);

    headerrow("Owner tags to apply.  Must have at least one.").appendTo(form);
    $("<div>", {"class": "row"})
	.append($("<div>", {"class": "col-2 text-right"}).html(getIcon("tag")))
	.append($("<div>", {"id": "owntags", "class": "col-10 h5"}))
	.on("click", toggleTag)
	.appendTo(form);

    headerrow("Other tags that can be applied.").appendTo(form);
    $("<div>", {"class": "row"})
	.append($("<div>", {"class": "col-2 text-right"}).html(getIcon("tag")))
	.append($("<div>", {"id": "opttags", "class": "col-10 h5"}))
	.on("click", toggleTag)
	.appendTo(form);

    headerrow("Optional metadata to apply").appendTo(form);

    form.append(selGrp("loc", getIcon("location"), "or be more specific"))
        .append(timegrp({"ts": "", "tz": ""})) // also has to be populated
        .append(selGrp("wmk", "Watermark", "or create new watermark"))
        .append(formgrp("inpcam", "text", "Camera"))
        .append(formgrp("inpflm", "text", "Film"))
        .append(formgrp("inppcs", "text", "Process"))
	.appendTo(cdiv);

    $("<div>", {"class": "row my-3"})
	.append($("<div>", {"class": "col text-right"})
		.append($("<button>", {"class": "btn btn-primary"})
			.html(getIcon("upload"))
			.on("click", doUpload)))
	.appendTo(cdiv);

    parent.append(cdiv);

    updateTags();
    for (let f of ["inpts", "inpdat", "inpcam", "inpflm", "inppcs"]) {
        $("#" + f).attr("placeholder", "use EXIF");
    }
    $("#inploc").off("change").on("change", updateLocation);
    $("#uploadfile").off("change").on("change", updateFile);
    $.ajax({"url": "/rest/suggest?wmk=1&tmz=1&loc=1",
            "type": "GET",
            "success": handleSuggestions});

}

function updateFile(event) {
    // the actual javascript spec replaces the local path in the
    // string with "C:\fakepath\", which, obviously, looks asinine.
    // javascript is gud computer
    let f = $(event.target).val().replace("C:\\fakepath\\", "");
    $(event.target).next(".custom-file-label").html(f);
}

function updateLocation() {
    $.ajax({"url": "/rest/suggest",
	    "type": "GET",
	    "data": { "loc": $("#inploc").val() },
	    "success": handleSuggestions});
    return false;
}

function doUpload() {
    let fd = new FormData();
    maybeSetFd(fd, "ts", $("#inpts").val());
    maybeSetFd(fd, "cam", $("#inpcam").val());
    maybeSetFd(fd, "flm", $("#inpflm").val());
    maybeSetFd(fd, "pcs", $("#inppcs").val());
    maybeSetFd(fd, "tz",  $("#fretz").val() || $("#inptz").val());
    maybeSetFd(fd, "loc", $("#inploc").val());
    maybeSetFd(fd, "nlc", $("#freloc").val());
    maybeSetFd(fd, "wmk", $("#frewmk").val() || $("#inpwmk").val());
    fd.append("file", $("#uploadfile")[0].files[0]);

    for (let t of $("#owntags").find("a")) {
	if ($(t).hasClass("badge-info")) {
	    fd.append("tag", t.id.split("_")[1]);
	}
    }
    for (let t of $("#opttags").find("a")) {
	if ($(t).hasClass("badge-info")) {
	    fd.append("tag", t.id.split("_")[1]);
	}
    }

    $.ajax({"url": "/rest/upload",
	    "type": "POST",
	    "data": fd,
	    "processData": false,
	    "contentType": false,
	    "success": uploadCallback});
}

function maybeSetFd(fd, name, val) {
    if (val != undefined && val != "") fd.set(name, val);
}

function uploadCallback(wut) {
    let cdiv = $("#content");
    cdiv.append($("<pre>").text(wut.ids));
    cdiv.append($("<pre>").text(wut.log));
    updateNotifications();
}

function updateTags() {
    $.ajax({"url": "/rest/tag",
	    "type": "GET",
	    "success": tagsCallback});
    return false;
}

function tagsCallback(json_tags) {
    let first = true;
    $("#owntags").empty();
    $("#opttags").empty();
    for (let t of json_tags) {
        let newtag = $("<a>", {"id": "tag_" + t.id,
		               "class": "badge badge-secondary mr-2 ",
		               "href": "#"});
	newtag.text(t.tag)
        if (t.lvl > 3) {
            if (first) {
                newtag.removeClass("badge-secondary")
                    .addClass("badge-info");
                first = false;
            }
	    newtag.appendTo($("#owntags"));
        } else {
            newtag.appendTo($("#opttags"));
        }
    }
}

function toggleTag(event) {
    let t = $(event.target);
    if (t.hasClass("badge-info")) {
	t.removeClass("badge-info").addClass("badge-secondary");
    } else if (t.hasClass("badge-secondary")) {
	t.removeClass("badge-secondary").addClass("badge-info");
    } else {
	console.log("wut is this?");
	console.log(t);
    }	
}
