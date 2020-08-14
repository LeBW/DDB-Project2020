# DDB-Project2020
分布式数据库 2020 课程项目。

## 项目结构
本项目是在`optionalSourceCode`的基础上完成。

* `src`: 存放源文件，包括`lockmgr`，`transaction`，`test` 三个文件目录。
    * `lockmgr`: 分布式锁的实现（框架代码中已经实现）
    * `transaction`: 包括对 Transaction Manager 和 Workflow Manager 的实现。
    * `test`: 包括测试代码，测试脚本，以及测试报告等。

## 运行说明
首先运行Register
```bash
cd src/lockmgr
make clean
make

cd ../transaction
make clean
make

make runregistry
```

然后新开一个命令行，进行测试
```bash
cd src/test
# 先清空记录
rm -rf results/*
rm -rf data/*
# 运行测试
export CLASSPATH=.:gnujaxp.jar
javac RunTests.java
java -DrmiPort=3345 RunTests MASTER.xml
```