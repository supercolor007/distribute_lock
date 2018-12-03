# distribute_lock
分布式锁

- 高效处理cas命令
  - 插入 setNX 
  - 删除 lua脚本 
- 即插即用
- 阿里云redis版本必须升级到支持lua的版本

```xml
        <dependency>
            <groupId>com.color</groupId>
            <artifactId>distribute_lock</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
```
