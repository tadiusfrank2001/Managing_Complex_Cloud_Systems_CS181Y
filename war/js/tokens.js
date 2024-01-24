import { getIcon } from "./icons.js";
import { pageBreak, pageBreakObj, perror } from "./nav.js";


export function getTokenPage() {
    $.ajax({url: "/rest/tag?v=1",
	    type: "GET",
	    dataType: "json",
	    success: renderTokenPage,
	    error: perror
	   });
}


export function renderTag(tag) {
    return $("<span>", {"class": "badge badge-info mr-2"}).text(tag);
}


export function checkCanWrite() {
    $.ajax({"url": "/rest/tag",
            "type": "GET",
            "dataType": "json",
            "error": perror,
            "success": function(json) {
                let canwrite = false;
                for (let tag of json) {
                    if (tag.lvl >= 3) canwrite = true;
                }
                setEditButtonsVisibility(canwrite);
            }
           });
}


function setEditButtonsVisibility(canwrite)
{
    if (canwrite === true) {
        $("#uploadbtn").removeClass("d-none");
        $("#editbtn").removeClass("d-none");
    } else {
        $("#uploadbtn").addClass("d-none");
        $("#editbtn").addClass("d-none");
    }
}


function renderTokenPage(json)
{
    let cdiv = $("#content");
    let parent = cdiv.parent();
    cdiv.detach().empty();

    if (json.length === 0) {
        // the server response was 0 valid tokens.  We don't render
        // most of the page.
        $("#topbar").addClass("d-none");
        $("body").css("background-color", "black");
        $("<div>", {"class": "row align-items-center"})
            .append($("<div>", {"class": "mx-auto"})
                    .append($("<img>",
                              {"class": "my-4",
                               "src": "/graphics/camera-logo-small.jpg"}))
                    .append(loginFrm))
            .appendTo(cdiv);
        // NWS weird syntax advisory: $( function() ) is how jquery
        // >=3.0 spells "document.ready( function() )
        $( function() {
            $("#btnrc").off("click").on("click", handleSubmitCode);
        } );
        parent.append(cdiv);
        return;
    }

    let canwrite = false;
    let canread = false;
    let extratags = false;
    for (let tag of json) {
        if (tag.lvl >= 3) canwrite = true;
        if (tag.lvl == 1) canread = true;
        if (tag.lvl == 0) extratags = true;
    }
    setEditButtonsVisibility(canwrite);

    // "enter a new access code" section
    $("#topbar").removeClass("d-none");
    $("body").css("background-color", "");
    cdiv.append(pageBreak("Add another code"));
    cdiv.append(loginFrm);
    $( function() {
        $("#btnrc").off("click").on("click", handleSubmitCode);
    } );

    // "tags you can edit" section
    let tagselect = $("<select>", {"class": "custom-select",
                                   "id": "seltag"});
    if (canwrite) {
        cdiv.append(pageBreak("Tags you can edit",
                              "For these tags, you can upload new images, " +
                              "change captions and other metadata, and " +
                              "create or delete access codes."));
        // we will render them on the page and also prepare the "create code"
        // select box at the same time
        let first = true;
        for (let tag of json) {
            if (tag.lvl >= 3) {
                if (!first) cdiv.append("<hr/>");
                cdiv.append(tagRow(tag));
                tagselect.append($("<option>", {"value": tag.id})
                                 .text(tag.tag));
                first = false;
            }
        }
    }

    // "tags anyone can use" section
    if (extratags) {
        cdiv.append(pageBreak("Tags anyone can use",
                              "No one owns these tags.  Anyone can apply " +
                              "them to images that they own, and anyone " +
                              "using the tag can create or delete access " +
                              "codes."));
        let first = true;
        for (let tag of json) {
            if (tag.lvl == 0) {
                if (!first) cdiv.append("<hr/>");
                cdiv.append(tagRow(tag));
                tagselect.append($("<option>", {"value": tag.id})
                                 .text(tag.tag));
                first = false;
            }
        }
    }

    // "tags you can view" section
    if (canread) {
        cdiv.append(pageBreak("Tags you can only view",
                              "You can see images with these tags, but " +
                              "you can't modify them or create more."));
        let tdiv = $("<div>", {"class": "row", "style": "font-size: 150%;"})
        for (let tag of json) {
            if (tag.lvl == 1 || tag.lvl == 2) {
                $("<div>", {"class": "col-auto"})
                    .append(renderTag(tag.tag))
                    .append(` (${tag.n})`)
                    .appendTo(tdiv);
            }
        }
        cdiv.append(tdiv);
    }

    // create new code section
    if (canwrite) {
        cdiv.append(newCodeFrm);
        $( function() {
            $("#divseltag").append(tagselect);
            $("#btnnew").off("click").on("click", handleCreateToken);
            $("#chkexp").off("change").on("change", handleSwitchToggle);
            $("#chkcnt").off("change").on("change", handleSwitchToggle);
        } );
    }

    // cookie managing section
    cdiv.append(cookieForm);
    $( function() {
        $("#inpcok").val(getTokens());
        $("#btnres").off("click").on("click", handleRestoreCookie);
        $("#btnclr").off("click").on("click", handleClearCookie);
    } );
    parent.append(cdiv);

}


const loginFrm = `
<form id="frmcode" class="form-inline">
  <input id="inprc" name="inprc" class="form-control mr-sm-2" type="text"/>
  <button id="btnrc" class="btn btn-primary mr-sm-2">
    ${getIcon("key").outerHTML}
  </button>
</form>
`;


const newCodeFrm = `
<div class="alert alert-primary py-2 my-3"><h4>
Create new access code
</h4></div>
<form id="newcodefrm">
  <div class="form-row my-2">
    <div class="col-auto py-2">Create a</div>
    <div class="col-auto">
      <select id="seltyp" class="custom-select">
        <option value=1 selected>View</option>
        <option value=3>Edit</option>
      </select>
    </div>
    <div class="col-auto py-2">code for</div>
    <div id="divseltag" class="col-auto"></div>
    <div class="col-auto">
      <button id="btnnew" class="btn btn-success">Create</button>
    </div>
  </div>
  <div class="form-row my-2">
    <div class="col-2 py-2">
      <div class="custom-control custom-switch">
        <input id="chkexp" class="custom-control-input" type="checkbox" />
        <label for="chkexp" class="custom-control-label">Expires after</label>
      </div>
    </div>
    <div class="col-auto">
      <input id="inpexp" class="form-control" disabled type="text" value="1" />
    </div>
    <div class="col-auto">
      <select id="selexp" class="custom-select" disabled>
        <option value="d">days</option>
        <option value="w">weeks</option>
        <option value="m" selected>months</option>
        <option value="y">years</option>
      </select>
    </div>
  </div>
  <div class="form-row my-2">
    <div class="col-2 py-2">
      <div class="custom-control custom-switch">
        <input id="chkcnt" class="custom-control-input" type="checkbox" />
        <label for="chkcnt" class="custom-control-label">Limit uses to</label>
      </div>
    </div>
    <div class="col-auto">
      <input id="inpcnt" class="form-control" disabled type="text" value="1" />
    </div>
  </div>
</form>
`;


const cookieForm = `
<div class="alert alert-primary py-2 my-3"><h4>
Manage your cookie
</h4></div>
<form id="cookiefrm">
  <p>This random string is a cookie with your current access tokens.
  You can save it in a password manager, and paste it back in to restore
  access if you lose it.</p>
  <div class="form-row">
    <div class="col">
      <input id="inpcok" class="form-control" type="text" />
    </div>
    <div class="col-auto">
      <button id="btnres" class="btn btn-danger">Restore</button>
    </div>
    <div class="col-auto">
      <button id="btnclr" class="btn btn-danger">
        Clear cookie (log out)
      </button>
    </div>
  </div>
</form>
`;


function tagRow(tag)
{
    let tdiv = $("<div>");
    let r = $("<div>", {"class": "row", "style": "font-size: 150%;"});
    $("<div>", {"class": "col-3"})
        .append(renderTag(tag.tag))
        .append(` (${tag.n})`)
        /* TODO: Hard to see how to make this button not confusing.
        .append("<br/>")
        .append($("<span>", {"class": "badge badge-danger p-1 mr-2"})
                .html(getIcon("x"), {"width": 18, "height": 18})
                .on("click", handleDropTag))
         */
        .appendTo(r);
    $("<div>", {"class": "col-9"})
        .append($("<p>").text(tag.dsc))
        .appendTo(r);
    tdiv.append(r);
    for (let tok of tag.tok) tdiv.append(tokenRow(tok));
    return tdiv;
}


function tokenRow(token)
{
    let ary = token.rc.split("");
    for (let i = 1; i < ary.length - 1; ++i) ary[i] = "·";
    let redacted = ary.join("");
    let expires = "Never expires.";
    if (token.exp) expires = `Expires: ${token.exp}`;
    let max = "";
    if (token.max > 0) max = ` of max ${token.max}`;
    let codetype = "View";
    if (token.lvl > 3) codetype = "Edit";

    let fancyspan = $("<span>", {"class": "text-monospace"})
        .text(redacted)
        .hover(function() { $(this).text(token.rc); },
               function() { $(this).text(redacted); })
        .on("click", function() { $(this).text(token.rc); });

    let r = $("<div>", {"class": "row"});
    $("<div>", {"class": "col-3"})
        .appendTo(r);
    $("<div>", {"class": "col-8"})
        .append($("<button>", {"id": "del_" + token.rc,
                               "class": "btn btn-primary p-1 mr-2"})
                .html(getIcon("x"), {"width": 18, "height": 18})
                .data("rc", token.rc)
                .on("click", handleDeleteToken))
        .append(`${codetype} code `)
        .append(fancyspan)
        .append(` (${expires} Use count: ${token.n}${max})`)
        .appendTo(r);
    return r;
}


// ========== event handler town ==========


function handleSwitchToggle(event)
{
    let elts = null;
    if (event.currentTarget.id === "chkexp") {
        elts = $("#inpexp,#selexp");
    } else if (event.target.id === "chkcnt") {
        elts = $("#inpcnt");
    } else {
        alert("wut?");
    }
    elts.attr("disabled", !event.target.checked);
}


function handleRestoreCookie(event)
{
    setTokens($("#inpcok").val());
    getTokenPage();
}


function handleClearCookie(event)
{
    setTokens("");
    getTokenPage();
}


function handleCreateToken(event)
{
    let fd = {};
    fd.new = true;
    fd.lvl = $("#seltyp").val();
    fd.tag = $("#seltag").val();
    if ($("#chkexp").prop("checked")) {
        let len = $("#inpexp").val() * 86400 * 1000;
        len *= {"d": 1, "w": 7, "m": 30, "y": 365}[$("#selexp").val()]
        fd.exp = Date.now() + len;
    }
    if ($("#chkcnt").prop("checked")) {
        fd.cnt = $("#inpcnt").val();
    }
    $.ajax({"url": "/servlet/token",
            "type": "POST",
            "data": fd,
            "success": getTokenPage,
            "error": perror});
}


function handleDeleteToken(event)
{
    let rc = $(event.currentTarget).data("rc");
    if (rc) {
        $.ajax({"url": "/servlet/token",
                "type": "POST",
                "data": {"del": rc},
                "success": getTokenPage,
                "error": perror});
    } else {
        console.log("handleDeleteToken has a sad: no data");
    }
}


function handleSubmitCode(event) {
    event.preventDefault();
    $.ajax({url: "/servlet/token",
	    data: { "rc": $("#inprc").val() },
	    type: "POST",
	    dataType: "text", // returns nothing on success
	    success: function() {
                // if we have exactly one token, we just "logged in,"
                // and should redirect to the front page rather than a
                // weird page about tokens.
                if (getTokens().split(":").length == 2) {
                    $("#topbar").removeClass("d-none");
                    $("body").css("background-color", "");
                    window.location.hash = "dr";
                } else {
                    getTokenPage();
                }
            },
	    error: function(xhr, status, err) {
		if (xhr.status == 403) {
		    alert = $("<span>", {"class": "badge badge-danger"})
			.text("Code not accepted.")
		    $("#frmcode").append(alert);
		} else {
		    perror(xhr, status, err);
		}
	    }});
}


// ========== do stuff to the token cookie functions ==========


function getTokens() {
    for (let c of document.cookie.split(";")) {
	c = decodeURIComponent(c.trim());
	if (c.indexOf("token=") == 0) { return c.substring(6); }
    }
}


function setTokens(t) {
    let d = new Date();
    d.setFullYear(d.getFullYear() + 1);
    let exp = d.toUTCString();
    document.cookie = `token=${t}; expires=${exp}; path=/`;
}
