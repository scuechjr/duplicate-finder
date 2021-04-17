# duplicate-finder
主要进行简单小文件的对比，目前对识别出来的重复文件仅支持直接删除或者迁移到扫描日志目录下，并未提供接口进行指定文件删除、查看和迁移。
![image](https://github.com/scuechjr/duplicate-finder/blob/main/image.png?raw=true)

## 开发/运行环境
maven、jdk9+

## 打包
```shell
# 进入工程所在目录
cd ./duplicate-finder
mvn clean -U packate -Dmaven.test.skip=true
```

## 运行
```shell
java -jar duplicate-finder-0.0.1-SNAPSHOT.jar
```

## 版本说明
* v0.0.1 2021-04-08
  >简单扫描重复文件，可对重复文件进行直接删除、迁移。
  > 
* v0.0.2 规划中

## 计划功能
* 支持指定扫描结果处理
* 支持对常见文件类型打开预览
* 支持按类型显示重复文件