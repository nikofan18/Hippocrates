var obj;
var typeVar = 'Type';
var rendered;
var resultsToShow = 10;
var totalResults = 0;
function sendQuery() {

    window.location.href = '#';
    document.getElementById('resultsContent').innerHTML = '';
    document.getElementById('foundHeader').style.display = 'none';
    document.getElementById('showMore').style.display = 'none';
    document.getElementById('searching').style.display = 'block';

    totalResults = 0;
        document.getElementById("query").innerHTML = document.getElementById("srch-term").value;
        $.ajax({
            method: "GET",
            url: "api/queries/" + encodeURIComponent(document.getElementById("srch-term").value) + "/type/" +
            typeVar,
            success: function (data) {
                obj = JSON.parse(data);
                rendered = obj.results;
                if(obj.results > resultsToShow)
                    document.getElementById('showMore').style.display = 'block';
                renderSearch(obj);
            }
        })
}

function renderSearch(obj) {

    document.getElementById('foundHeader').style.display = 'block';
    document.getElementById("resultsContent").innerHTML = "";
    document.getElementById("resultsNum").innerHTML = obj.results;
    document.getElementById('searching').style.display = 'none';

    if(obj.results === 0){
        document.getElementById("noResults").style.display = 'block';
        document.getElementById("queryHeader").style.display = 'block';
        document.getElementById("foundHeader").style.display = 'none';
        // document.getElementById('showMore').style.display = 'none';
    }
    else {
        document.getElementById("queryHeader").style.display = 'block';
        document.getElementById("foundHeader").style.display = 'block';
        document.getElementById("noResults").style.display = 'none';
    }


    document.getElementById("ms").innerHTML = obj.time;
    renderNResults(resultsToShow);
}
function renderNResults(n) {
    for(var i=rendered - 1; i >= rendered - n; i--){
        console.log(i);
        if(i < 0) {
            document.getElementById('showMore').style.display = 'none';
            break;
        }
        document.getElementById('resultsContent').innerHTML += '' +
            '<div id="'+obj["doc"+i].path+'">'+
            //    data-toggle="modal" data-target="#myModal"
            //    onclick="showContent(this.id)"
            // href="'+obj["doc"+i].path+'"
            '<a onclick="showContent(this.id)" data-toggle="modal"  id="'+obj["doc"+i].path+'" >'+obj["doc"+i].name+'</a>'+
            '<h5>'+obj["doc"+i].path+'</h5>'+
            '<h6>Score: '+obj["doc"+i].score+'</h6>'+
            '</div>' +
            '<hr class="style-six">';
        totalResults++;
    }
    document.getElementById('showingResults').innerHTML = totalResults.toString();
    rendered -= n;
}

function showContent(id) {
    $("#div3").load(id);
    $('#myModal').modal('toggle');
}

$( document ).ready(function() {
    $(".dropdown-menu li a").click(function(){
        var selText = $(this).text();
        typeVar = selText;
        $(this).parents('.btn-group').find('.dropdown-toggle').html(selText+' <span class="caret"></span>');
    });
});