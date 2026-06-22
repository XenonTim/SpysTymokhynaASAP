<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="uk">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Sign up ASAP</title>

  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
  <style>
    html, body { height: 100%; }
    body {
      display: flex;
      align-items: center;
      padding-top: 40px;
      padding-bottom: 40px;
      background: linear-gradient(135deg, #879cfa 0%, #9a5fd6 100%);
    }
    .form-signin {
      max-width: 330px;
      padding: 15px;
      background-color: rgb(255 255 255 / 0.78);
      border-radius: 15px;
      box-shadow: 0 10px 30px rgba(0,0,0,0.15);
    }
    .form-signin .form-floating:focus-within { z-index: 2; }
    .form-signin input[type="text"] { margin-bottom: -1px; border-bottom-right-radius: 0; border-bottom-left-radius: 0; }
    .form-signin input[type="email"] { margin-bottom: -1px; border-bottom-right-radius: 0; border-bottom-left-radius: 0; }
    .form-signin input[type="password"] { margin-bottom: 10px; border-top-left-radius: 0; border-top-right-radius: 0; }
  </style>
</head>
<body>

<main class="form-signin w-100 m-auto text-center">
  <form action="sign_up" method="POST">
    <h1 class="h3 mb-3 fw-normal">Sign up</h1>

    <c:if test="${not empty error}">
      <div class="alert alert-danger py-2">${error}</div>
    </c:if>

    <div class="form-floating">
      <input type="text" name="username" class="form-control" id="floatingInput" placeholder="Username" required>
      <label for="floatingInput">Username</label>
    </div>
    <div class="form-floating">
      <input type="email" name="login" class="form-control" id="floatingInput" placeholder="Login" required>
      <label for="floatingInput">Login</label>
    </div>
    <div class="form-floating">
      <input type="password" name="password" class="form-control" id="floatingPassword" placeholder="Password" required>
      <label for="floatingPassword">Password</label>
    </div>

    <button class="btn btn-primary w-100 py-2 mt-3" type="submit">Sign up</button>
    <button class="btn btn-primary w-100 py-2 mt-3" type="submit">Log in</button>
  </form>
</main>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
