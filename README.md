### 项目简介
点评是一个基于Java开发的应用，旨在为用户提供便捷的商家评价和搜索服务。用户可以通过该平台查找附近的商家，查看其他用户的评价，并发表自己的点评内容，帮助其他用户做出更明智的选择。

### 技术栈
- **后端**：Java、Spring Boot、Spring MVC、MyBatis-Plus
- **数据库**：MySQL、Redis
- **前端**：HTML、CSS、JavaScript、VUE
- **其他**：Maven

### 功能模块
1. **用户管理**
    - 用户注册与登录
2. **商家管理**
    - 商家信息展示
    - 商家搜索
    - 商家分类
    - 商家排序
3. **点评管理**
    - 用户发表点评
    - 用户关注
    - 点评点赞与回复
4. **系统管理**
    - 数据统计

### 项目结构
```
HMComments
├── src
│   └── main
│      ├── java
│      │   └── com.hmdp
│      │       ├── config
│      │       ├── controller
│      │       ├── dto
│      │       ├── interceptor
│      │       ├── service
│      │       ├── mapper
│      │       ├── entity
│      │       └── utils
│      └── resources
│          ├── mapper
│          └── db
│
├── pom.xml
└── README.md
└── .gitignore
```
