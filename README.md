<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#usage">Installation / Usage</a></li>
      </ul>
      <ul>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
  </ol>
</details>

### About The Project
cURL like command implementation 

### Usage

- To Run the server simply type 'java httpfs' into the terminal or simply run the code. Use -v, -p and -d to specify verbose, port and directory  options respectively.

- For example:
  - "java httpfs"
  - "java httpfs -v -p 5047 -d ./NewDir"


- Use the following commands in the client to test the server in the bin directory of the client(httpc) application : 

  - java com.gcs.cn.httpc get "http://localhost:8080"
  - java com.gcs.cn.httpc get "http://localhost:8080" -h "Accept: application/xml"
  - java com.gcs.cn.httpc get "http://localhost:8080" -h "Accept: application/json"
  - java com.gcs.cn.httpc get "http://localhost:8080" -h "Accept: text/html"
  - java com.gcs.cn.httpc get "http://localhost:8080" -h "Accept: text/plain"
  - java com.gcs.cn.httpc get "http://localhost:8080/hello.txt"
  - java com.gcs.cn.httpc post "http://localhost:8080/File" -d "This will be the written in the file by Nilesh"
  - java com.gcs.cn.httpc post "http://localhost:8080/File?overwrite=true" -d "This will be overwritten in the file by Neha today"

### Built With

* ![Java](https://img.shields.io/badge/java-red?style=for-the-badge&logo=Java&logoColor=red)

