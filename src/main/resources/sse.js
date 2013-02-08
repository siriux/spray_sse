(function () {

function SSE(url) {
  this.url = url;
  this.initialized = false;

  var self = this;

  evtSource = new EventSource(this.url + "/receive");
  evtSource.addEventListener("init", function(msg) {
    var jsonMsg = JSON.parse(msg.data);
    self.id = jsonMsg.id;
    self.token = jsonMsg.token;
    self.initialized = true;
    if (self.onopen) {
      self.onopen()
    }
  });

  evtSource.addEventListener("message", function(msg){
    if (self.onmessage) {
      self.onmessage(msg);
    }
  });

  evtSource.onerror = function(e){
    if (self.onerror) {
        self.onerror(e);
    }
  };

  this.evtSource = evtSource;
}

SSE.prototype.send = function(msg) {
  if (this.initialized) {
    var req = new XMLHttpRequest();
    req.addEventListener("error", function(e) {
      if (self.onerror) {
        self.onerror(e);
      }
    }, false); 
    req.open("POST", this.url + "/send", true);
    req.setRequestHeader("Content-type","application/x-www-form-urlencoded");
    req.send("id=" + this.id + "&token=" + this.token + "&msg=" + msg);

  } else {
    console.log("Not initialized")
  }
}

SSE.prototype.close = function() { this.evtSource.close(); }

window.SSE = SSE;

}());