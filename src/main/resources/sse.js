(function () {

function SSE(url) {
  this.url = url;
  this.initialized = false;
  this.onMessage = function(e){ console.log(e); }; // Default handler logs

  var self = this;

  this.evtSource = new EventSource(this.url + "/receive");
  this.evtSource.addEventListener("init", function(msg) {
    var jsonMsg = JSON.parse(msg.data);
    self.id = jsonMsg.id;
    self.token = jsonMsg.token;
    self.initialized = true;
    if (self.onInit) {
      self.onInit()
    }
  });

  this.evtSource.addEventListener("message", function(msg){ self.onMessage(msg.data); });
}

SSE.prototype.send = function(msg) {
  if (this.initialized) {
    var request = new XMLHttpRequest();
     
    request.open("POST", this.url + "/send", true);
    request.setRequestHeader("Content-type","application/x-www-form-urlencoded");
    request.send("id=" + this.id + "&token=" + this.token + "&msg=" + msg);
    // TODO handle errors here
  } else {
    console.log("Not initialized")
  }
}

window.SSE = SSE;

}());