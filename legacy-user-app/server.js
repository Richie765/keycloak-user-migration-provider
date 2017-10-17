const express = require('express')
const morgan = require('morgan')
const bodyParser = require('body-parser')
const HttpStatus = require('http-status-codes');
const bcrypt = require('bcrypt');


// Settings

const port = process.env.PORT || 9081;
const url_prefix = '/migration';


// Users

const users = require('./users.json');

let userMap = {};
users.forEach(user => userMap[user.username] = user);


// Create server instance

const app = express()

app.use(morgan('dev', {
  // skip: function (req, res) { return res.statusCode < 400 }
}));

app.use(bodyParser.json({ type: 'application/json' }));


// Routes

app.get('/', (req, res) => {
  res.status(HttpStatus.NOT_FOUND)
  .send(HttpStatus.getStatusText(HttpStatus.NOT_FOUND));
});


// validateUserExists

app.head(`${ url_prefix }/api/users/:username/`, (req, res) => {
  const username = req.params.username;
  const user = userMap[username];

  // User exists
  let status = user ? HttpStatus.OK : HttpStatus.NOT_FOUND;

  res.status(status)
  .send(HttpStatus.getStatusText(status));
});


// getUserDetails

app.get(`${ url_prefix }/api/users/:username/`, (req, res) => {
  const username = req.params.username;
  let user = userMap[username];
  user = Object.assign({}, user);

  delete user.passwordHash;
  
  if(user) {
    res.status(HttpStatus.OK)
    .json(user);
  }
  else {
    res.status(HttpStatus.NO_CONTENT)
    .send(HttpStatus.getStatusText(HttpStatus.NO_CONTENT));  
  }
});


// validateLogin

app.post(`${ url_prefix }/api/users/:username/`, (req, res) => {

  const username = req.params.username;
  const user = userMap[username];
  
  const password = req.body.password;
  const passwordHash = user.passwordHash;

  bcrypt.compare(password, passwordHash, function(err, isValid) {
    let status;

    if(err) {
      console.error(err);
      status = HttpStatus.UNAUTHORIZED;
    }
    else {
      if(isValid) {
        status = HttpStatus.OK;
      }
      else {
        status = HttpStatus.FORBIDDEN;
      }
    }

    res.status(status)
    .send(HttpStatus.getStatusText(status));
  });
});


// Fire up the server

app.set('port', port);

app.listen(port, () => {
  const url = `http://localhost:${ port }`;

  console.log(`Application running on: ${ url }`);
})
