package com.xuecheng.ucenter.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.mapper.XcMenuMapper;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcMenu;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security 用户详情服务实现类
 *
 * 核心职责：
 * 1. 接收认证请求，根据认证类型（authType）分发到对应的 AuthService 实现
 * 2. 构建 UserDetails 对象供 Spring Security 进行密码校验和权限控制
 *
 * 支持的认证类型通过 bean 命名约定路由：{authType}_authservice
 * 例如：password_authservice、wx_authservice
 */
@Slf4j
@Component
public class UserServiceImpl implements UserDetailsService {

    @Autowired
    XcUserMapper xcUserMapper;

    @Autowired
    XcMenuMapper xcMenuMapper;

    @Autowired
    ApplicationContext applicationContext;

    /**
     * Spring Security 核心方法 —— 根据用户名加载用户信息
     *
     * 这里的参数 s 并非原始用户名，而是将 AuthParamsDto 序列化后的 JSON 字符串，
     * 前端在认证时传入的认证参数（包括认证类型、账号、密码等）都封装在其中。
     *
     * @param s AuthParamsDto 的 JSON 字符串
     * @return UserDetails 对象（包含用户信息、密码、权限列表）
     */
    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {

        AuthParamsDto authParamsDto = null;
        try {
            // 将认证参数字符串解析为 AuthParamsDto 对象
            authParamsDto = JSON.parseObject(s, AuthParamsDto.class);
        } catch (Exception e) {
            log.info("认证请求不符合项目要求:{}",s);
            throw new RuntimeException("认证请求数据格式不对");
        }

        // 根据认证类型（如 password、wx）获取对应的 bean
        String authType = authParamsDto.getAuthType();
        String beanName = authType + "_authservice";
        // 从 Spring 容器中获取对应的 AuthService 实现类
        AuthService authService = applicationContext.getBean(beanName, AuthService.class);
        // 执行认证逻辑（不同认证类型有不同的实现）
        XcUserExt xcUserExt = authService.execute(authParamsDto);

        return getUserPrincipal(xcUserExt);
    }

    /**
     * 构建 UserDetails 对象
     *
     * 1. 从数据库查询用户的权限菜单
     * 2. 将用户信息转为 JSON 存储在 UserDetails 的 username 字段中
     * 3. 密码保持不变，供 Spring Security 进行比对
     *
     * @param user 用户扩展信息
     * @return Spring Security 的 UserDetails 对象
     */
    public UserDetails getUserPrincipal(XcUserExt user) {
        // 设置默认权限，防止 null 导致异常（Cannot pass a null GrantedAuthority collection）
        String[] authorities = {"test"};
        // 从数据库查询该用户的实际权限列表
        List<XcMenu> xcMenus = xcMenuMapper.selectPermissionByUserId(user.getId());
        if(xcMenus.size()>0) {
            List<String> permissions = new ArrayList<>();
            xcMenus.forEach(m -> {
                permissions.add(m.getCode());
            });
            authorities = permissions.toArray(new String[0]);
        }

        String password = user.getPassword();
        user.setPassword(null);  // 清除密码，避免用户信息泄露
        // 将用户对象转为 JSON 字符串，作为 UserDetails 的 username 存储
        // 后续在控制器中可以从 authentication 中获取完整的用户信息
        String userString = JSON.toJSONString(user);

        // 构建 UserDetails：username 存储完整用户信息（JSON），password 用于密码校验
        return User.withUsername(userString).password(password).authorities(authorities).build();
    }
}
