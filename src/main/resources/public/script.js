var obj;
var typeVar = 'Choose Type';

function sendQuery() {
        document.getElementById("query").innerHTML = document.getElementById("srch-term").value;
        $.ajax({
            method: "GET",
            url: "api/queries/" + document.getElementById("srch-term").value + "/type/" +
            typeVar,
            success: function (data) {
                obj = JSON.parse(data);
                renderSearch(obj);
            }
        })
}

function renderSearch(obj) {
    document.getElementById("resultsContent").innerHTML = "";
    document.getElementById("resultsNum").innerHTML = obj.results;
    if(obj.results === 0){
        document.getElementById("noResults").style.display = 'block';
        document.getElementById("queryHeader").style.display = 'block';
        document.getElementById("foundHeader").style.display = 'none';
    }
    else {
        document.getElementById("queryHeader").style.display = 'block';
        document.getElementById("foundHeader").style.display = 'block';
        document.getElementById("noResults").style.display = 'none';
    }


    document.getElementById("ms").innerHTML = obj.time;
    for(var i=0; i < obj.results; i++){
        document.getElementById('resultsContent').innerHTML += '' +
        '<div id="'+obj["doc"+i].path+'">'+
            //    data-toggle="modal" data-target="#myModal"
            //    onclick="showContent(this.id)"
            '<a id="'+obj["doc"+i].path+'" href="'+obj["doc"+i].path+'" >Document Name</a>'+
            '<h5>'+obj["doc"+i].path+'</h5>'+
            '<h6>Score: '+obj["doc"+i].score+'</h6>'+
        '</div>' +
        '<hr class="style-six">';
    }
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