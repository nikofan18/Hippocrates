function sendQuery() {
    $.ajax({
        method: "GET",
        url: "api/queries/" + document.getElementById("searchInput").value,
        success: function (data) {
            var obj = JSON.parse(data);
            alert(obj.query);
        }
    })
}