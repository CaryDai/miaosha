package com.miaosha;

import com.miaosha.dao.UserDoMapper;
import com.miaosha.dataobject.UserDo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hello world!
 *
 */
//@EnableAutoConfiguration    // 将这个类当成可以支持自动化配置的bean，并且开启这个类的基于springboot的自动化配置
// 扫描项目下的@Controller、@Service等注解，将其作为bean加入Spring容器
@SpringBootApplication(scanBasePackages = {"com.miaosha"})
@MapperScan("com.miaosha.dao")
@RestController
public class App 
{

    @Autowired
    private UserDoMapper userDoMapper;

    @RequestMapping("/")
    public String home() {
        UserDo userDo = userDoMapper.selectByPrimaryKey(1);
        if (userDo == null) {
            return "用户对象不存在";
        } else {
            return userDo.getName();
        }
    }

    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        SpringApplication.run(App.class, args);
    }
}
