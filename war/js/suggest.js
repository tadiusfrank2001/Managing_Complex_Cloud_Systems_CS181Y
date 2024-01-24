export function handleSuggestions(json) {
    if (json.wmk !== undefined) populateSelect(json.wmk, "wmk");
    if (json.tmz !== undefined) populateSelect(json.tmz, "tz");
    if (json.loc !== undefined) updateLocationGrp(json.loc);
}

function populateSelect(json, field) {
    let sel = $("#inp" + field);
    sel.empty();
    for (let i of json.opt) {
        let opt = $("<option>", {"value": i})
            .text(i);
        if (json.dfl === i) opt.attr("selected", "");
        opt.appendTo(sel);
    }
}

// update the previously created locationgrp using the options
// supplied in a json_loc array.  This is an array of objects where
// each object has the attributes "id", "txt", and "rel".  id will be
// set as the dropdown entry "value" and is probably going to be a
// database id.  txt is what is displayed in the dropdown entry.  rel
// is "p" for parent, "c" for child, "s" for suggestion, or undefined
// to indicate the currently selected value.

function updateLocationGrp(json_loc) {
    if (json_loc === undefined) return;
    $("#freloc").val("");
    let sel = $("#inploc");
    sel.empty();
    // don't detach and re-append, or you will mess up the order
    for (let i of json_loc) {
	let opt = $("<option>", {"value": i.id})
	if (i.rel == "p") {
	    opt.text("◀ " + i.txt); // oh god oh god
	} else if (i.rel == "c") {
	    opt.text("▶ " + i.txt); // yolo
	} else if (i.rel == "s") {
	    opt.text(i.txt);
	} else if (i.rel === undefined) {
	    opt.attr("selected", "");
	    opt.text(i.txt);
	}
	sel.append(opt);
    }
}
