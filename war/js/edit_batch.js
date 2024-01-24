import { getIcon } from "./icons.js";
import { formgrp, selGrp, timegrp, TagGrp, PopulateTagGrp } from "./inputs.js";
import { handleSuggestions } from "./suggest.js";
import { perror } from "./nav.js";
import { Slide } from "./display.js";

const REST_URL = "/rest/edit";

// Exported functions ========================================================

export function RenderBatchEditForm(cdiv) {
    cdiv.empty();

    for (let id of $(window).data("imgid_list")) {
        let s = Slide($(window).data("imgid_map")[id])
            .on("click", function(e) { toggleSelected(id); });
        cdiv.append(s);
        setSelectedBorder(id);
    }

    let ediv = $("<div>", {class: "fixed-bottom p-3"})
        .css("background-color", $("body").css("background-color"))
        .appendTo(cdiv);

    $("<form>", {"id": "frmbatch"})
        .append(formgrp("inpcap", "text", "Caption"))
        .append(TagGrp(getIcon("tag"), "tag", prepareBatchUpdate))
        .append("<hr/>")
        .append(TagGrp(getIcon("person"), "ppl", prepareBatchUpdate))
        .append("<hr/>")
        .append(selGrp("loc", getIcon("location"), "or be more specific"))
        .append(selGrp("wmk", "Watermark", "or create new watermark"))
        .append(timegrp({}))
        .on("change", prepareBatchUpdate)
	.appendTo(ediv);

    $("<div>", {"class": "row my-3"})
        .append($("<div>", {"class": "col text-right"})
                .append($("<button>", {"class": "btn btn-primary mr-3"})
                        .text("All")
                        .on("click", function(e) { handleSelAll(true); }))
                .append($("<button>", {"class": "btn btn-primary mr-3"})
                        .text("None")
                        .on("click", function(e) { handleSelAll(false); }))
                .append($("<button>", {"class": "btn btn-primary mr-3"})
                        .text("Invert")
                        .on("click", function(e) { handleSelAll(); }))
                .append($("<button>", {id: "btncommit",
                                       class: "btn btn-primary mr-3 disabled"})
                        .text("Commit")
                        .on("click", executeBatchUpdate))
                .append($("<button>", {id: "btncancel",
                                       class: "btn btn-primary mr-3 disabled"})
                        .text("Cancel")
                        .on("click", resetBatchUpdate))
                .append($("<button>", {id: "btndelete",
                                       class: "btn btn-primary mr-3 disabled"})
                        .text("Delete")
                        .on("click", handleDeleteBatch)))
        .appendTo(ediv);

    // have to make sure the content div is tall enough that you can
    // see the bottom row when you scroll down
    $("<div>", {height: ediv.height()}).appendTo(cdiv);
    populateBatchEditForm();
}

// Subroutines ===============================================================


// Relying on $(window).data(), examine the set of selected photos,
// and for each attribute, determine how to represent them in the UI
// as a single value or as a "Varies".  This is hard.

function populateBatchEditForm() {
    // we need suggestions to be ready, and there's no way to guarantee
    // it is done before you get here lol.
    if (!$("#frmbatch").data("suggestdone")) {
        // do an ajax to get watermark and timezone ready
        $.ajax({url: "/rest/suggest?wmk=1&tmz=1",
                type: "GET",
                success: function(json) {
                    handleSuggestions(json);
                    $("<option>", {value: 0})
                        .text("(Varies)")
                        .appendTo($("#inpwmk"));
                    $("<option>", {value: 0})
                        .text("(Varies)")
                        .appendTo($("#inptz"));
                    $("#frmbatch").data("suggestdone", true);
                    populateBatchEditForm();
                },
                error: perror});
        return;
    }

    let batch = {};
    $("#frmbatch").data("batchdata", batch);

    // first we are making a map of field names to { last, varies }
    // objects.  if varies==true then the batch contains multiple
    // values in this field.  otherwise, 'last' is the single value
    // they all have.
    //
    // for our array-like values "tag" and "ppl", it is even more
    // complicated.  batch["tag"] will be a map of {id: {act,
    // varies}}.

    for (let pic of selectedPicList()) {
        for (let field of ["cap", "ts", "tz", "wmk"]) {
            if (batch[field] === undefined) {
                batch[field] = { last: pic[field], varies: false };
            } else if (pic[field] != batch[field].last) {
                batch[field].varies = true;
            }
        }
        if (batch.loc === undefined) {
            batch.loc = pic.loc[0];
        } else if (pic.loc[0].id != batch.loc.id) {
            batch.loc.varies = true;
        }
        for (let aryf of ["tag", "ppl"]) {
            if (batch[aryf] === undefined) {
                // this is the first selected image; copy its tag
                // array to the batch map.  Full deep copy is
                // necessary because mergeBatchTags() is going to
                // modify everything.
                batch[aryf] = [];
                for (let i of pic[aryf]) {
                    if (i.act) {
                        batch[aryf].push({id: i.id, act: true, txt: i.txt});
                    }
                }
            } else {
                // this is not the first image; let mergeBatchTags()
                // sort out what the differences are
                mergeBatchTags(batch[aryf], pic[aryf]);
            }
        }
    }

    // update the simple elements to show current value or "Varies"
    for (let field of ["cap", "ts", "tz", "wmk"]) {
        if (batch[field] === undefined) {
            $("#inp" + field).val("");
        } else if (batch[field].varies === false) {
            $("#inp" + field).val(batch[field].last);
        } else {
            $("#inp" + field).attr("placeholder", "Varies")
                .val("");
        }
    }

    // weird handling of location: if it is unsynchronized, then the
    // options you can choose are the values already present in the
    // batch.  if it is synchronized, then it acts like the
    // single-edit location box.
    let loc_opts = [];
    if (batch.loc === undefined) {
        $("#inploc").empty();
    } else if (batch.loc.varies) {
        let already_seen = {};
        loc_opts.push({id: 0, txt: "(Varies)"});
        for (let pic of selectedPicList()) {
            if (already_seen[pic.loc[0].id] === undefined) {
                loc_opts.push({id: pic.loc[0].id,
                               txt: pic.loc[0].txt,
                               rel: "s"});
                already_seen[pic.loc[0].id] = true;
            }
        }
        handleSuggestions({loc: loc_opts});
    } else {
        // go ask for the suggested options for location box
        let locid = batch.loc.id;
        if (locid != batch.locsuggests) {
            $.ajax({url: "/rest/suggest?loc=" + locid,
                    type: "GET",
                    success: function(json) {
                        handleSuggestions(json);
                        batch.locsuggests = locid;
                    }});
        }
    }

    PopulateTagGrp(batch, "tag");
    PopulateTagGrp(batch, "ppl");
}

// given...
// batch: a map of { id: { txt: "xyz", act: true, id: id, [varies=true]} }
// next: an array of [ {txt: "xyz", act: true, id: id }, ... ]
// ...update batch and set varies=true for any entries that differ

function mergeBatchTags(accum, next) {
    // pass 1, search inside accum for each item in next
    for (let n of next) {
        let found = false;
        // ignore suggestions for purpose of batch aggregation
        if (!n.act) { continue; }
        for (let a of accum) {
            if (n.id == a.id) {
                found = true;
                if (n.act != a.act || n.txt != a.txt) {
                    a.varies = true;
                }
                break;
            }
        }
        if (!found) {
            accum.push({act: n.act, txt: n.txt, id: n.id, varies: true});
        }
    }
    // pass 2, search inside next for each item in accum
    for (let a of accum) {
        let found = false;
        for (let n of next) {
            if (a.id == n.id) { found = true; break; }
        }
        if (!found) { a.varies = true; }
    }
}

// Check if any pending changes are sitting on batchdata, and enable
// the commit/cancel/delete buttons if so.
function enableCommit() {
    let batch = $("#frmbatch").data("batchdata");
    let commit_ready = false;
    if (batch !== undefined) {
        for (let [k, v] of Object.entries(batch)) {
            switch (k) {
            case "ppl":
            case "tag":
                for (let i of v) {
                    if (i.newval !== undefined) commit_ready = true;
                }
                break;
            default:
                if (v.newval !== undefined) commit_ready = true;
                break;
            }
        }
    }
    if (commit_ready) {
        $("#btncommit")
            .removeClass("disabled")
            .removeClass("btn-primary")
            .addClass("btn-success");
        $("#btndelete")
            .removeClass("disabled")
            .removeClass("btn-primary")
            .addClass("btn-danger");
        $("#btncancel")
            .removeClass("disabled");
    } else {
        $("#btncommit")
            .addClass("disabled")
            .addClass("btn-primary")
            .removeClass("btn-success");
        $("#btndelete")
            .addClass("disabled")
            .addClass("btn-primary")
            .removeClass("btn-danger");
        $("#btncancel")
            .addClass("disabled");
    }
}

// an iterable array of just the pic objects that are selected.
function selectedPicList() {
    let selected_pics = [];
    for (let id of $(window).data("imgid_list")) {
        let pic = $(window).data("imgid_map")[id];
        if (pic.selected) {
            selected_pics.push(pic);
        }
    }
    return selected_pics;
}

// change the "selected" state of everything in the window.
function handleSelAll(newstate) {
    for (let id of $(window).data("imgid_list")) {
        let pic = $(window).data("imgid_map")[id];
        if (newstate === undefined) {
            pic.selected = !pic.selected;
        } else {
            pic.selected = newstate;
        }
        setSelectedBorder(id);
    }
    resetBatchUpdate();
    populateBatchEditForm();
}

// toggle the "selected" state of a single image by id.
function toggleSelected(id) {
    let pic = $(window).data("imgid_map")[id];
    $("#imgid").text(id);
    if (pic === undefined) {
        console.log("bogus toggle event on id: " + id);
        return false;
    }
    pic.selected = !pic.selected;
    setSelectedBorder(id);
    resetBatchUpdate();
    populateBatchEditForm();
    return false;
}

// for a given id, change its UI box appearance to indicate whether it
// is selected or not.
function setSelectedBorder(id) {
    let is_selected = $(window).data("imgid_map")[id].selected;
    let newcol = ( is_selected ? "lightgreen" : "white" );
    $("#slide" + id).css("background-color", newcol);
}

// Event handlers ============================================================

// Interpret a change to the edit form and decorate batchdata with the
// proposed new value.  Then enable the "Commit" etc buttons if there
// is a nonzero pending change.

function prepareBatchUpdate(event) {
    let batch = $("#frmbatch").data("batchdata");
    let newval = event.target.value;
    let fname = event.target.id.substring(3);

    switch (event.target.id) {
    case "inpcap":
    case "inpwmk":
    case "inpts":
    case "inptz":
        if (batch[fname].varies || batch[fname].last != newval) {
            $(event.target).addClass("is-valid");
            batch[fname].newval = newval;
        } else {
            $(event.target).removeClass("is-valid");
            delete batch[fname].newval;
        }
        break;
    case "fretag":
    case "freppl":
    case "freloc":
    case "frewmk":
    case "fretz":
        if (newval !== undefined && newval.length > 0) {
            $(event.target).addClass("is-valid");
            batch[fname + "_fre"] = {newval: newval};
        } else {
            $(event.target).removeClass("is-valid");
            delete batch[fname + "_fre"];
        }
        break;
    case "inploc":
        if (batch.loc.varies || batch.loc.id != newval) {
            $(event.target).addClass("is-valid");
            batch.loc.newval = parseInt(newval);
        } else {
            $(event.target).removeClass("is-valid");
            delete batch.loc.newval;
        }
        break;
    default:
        // lol watch out and don't try to call 'fname' 'field',
        // because this one down here overwrites it.
        let [typ, field, id] = event.target.id.split("_");
        if (typ != "tag") {
            console.log("unhandled event target: " + event.target.id);
            return;
        }
        for (let i of batch[field]) {
            if (i.id == id) {
                switch (i.newval) {
                case true:
                    i.newval = false;
                    break;
                case false:
                    delete i.newval;
                    break;
                default: // matches undefined
                    i.newval = true;
                    break;
                }
            }
        }
        // change badge colors to show new state
        PopulateTagGrp(batch, field);
        break;
    }

    enableCommit();
    return false;
}

// Search batchdata and delete any pending changes.
function resetBatchUpdate(event) {
    populateBatchEditForm();
    $("#frmbatch").find(".is-valid").removeClass("is-valid");
    for (let field of ["tag", "ppl", "loc", "wmk", "tz"]) {
        $("#fre" + field).val("");
    }
    enableCommit();
}

function executeBatchUpdate(event) {
    let batch = $("#frmbatch").data("batchdata");
    let updates = [];
    $("#frmbatch").data("updates", updates);

    for (let pic of selectedPicList()) {
        let update = { img: pic.id };
        for (let [k, v] of Object.entries(batch)) {
            switch (k) {
            case "ppl":
            case "tag":
                break;
            default:
                if (v.newval !== undefined) {
                    update[k] = v.newval;
                }
            }
        }
        if (Object.keys(update).length > 1) { updates.push(update); }
        for (let field of ["ppl", "tag"]) {
            for (let i of batch[field]) {
                if (i.newval !== undefined) {
                    update = { img: pic.id };
                    if (i.newval) {
                        update[field] = i.id;
                    } else {
                        update[field + "_del"] = i.id;
                    }
                    updates.push(update);
                }
            }
        }
    }

    // and awaaaaay we go.
    runUpdateList();
}

// these are fun, they call each other in a chain until the update
// list is gone.

function runUpdateList() {
    let updates = $("#frmbatch").data("updates");
    if (updates.length > 0) {
        $("#btncommit").text("Committing " + updates.length);
        let u = updates.shift();
        $.ajax({url: REST_URL,
                type: "POST",
                data: u,
                success: function() { refreshPhoto(u.img); },
                error: perror});
    } else {
        $("#btncommit").text("Commit");
        resetBatchUpdate();
    }
}

function refreshPhoto(id) {
    $.ajax({url: REST_URL,
            type: "GET",
            data: {"img": id},
            success: function(json) {
                $(window).data("imgid_map")[id] = json;
                // note selected flag which was lost
                $(window).data("imgid_map")[id].selected = true;
                runUpdateList();
            },
            error: perror});
}

function handleDeleteBatch(event) { }

