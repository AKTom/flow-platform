package com.flow.platform.api.service.user;

import com.flow.platform.api.domain.response.LoginResponse;
import com.flow.platform.api.domain.user.User;
import java.util.Collection;
import java.util.List;

/**
 * @author liangpengyv
 */
public interface UserService {

    /**
     * List all users
     */
    List<User> list(boolean withFlow, boolean withRole);

    /**
     * List users by email list
     */
    List<User> list(Collection<String> emails);

    /**
     * Find user by email
     */
    User findByEmail(String email);

    /**
     * Find user by token
     */
    User findByToken(String token);

    /**
     * Login
     */
    LoginResponse login(String emailOrUsername, String rawPassword);

    /**
     * Register user to roles
     *
     * @param roles role name set, or null for not set to role
     */
    User register(User user, List<String> roles, boolean isSendEmail, List<String> flowsList);

    /**
     * Change password
     *
     * @param user user instance
     * @param oldPassword old raw password, directly change to new password if set to null
     * @param newPassword new raw password
     */
    void changePassword(User user, String oldPassword, String newPassword);

    /**
     * Delete a user
     */
    void delete(List<String> emailList);

    /**
     * update user role
     */
    List<User> updateUserRole(List<String> emailList, List<String> roles);

    /**
     * calculate user for admin count
     */
    Long adminUserCount();

    /**
     * calculate user total
     */
    Long usersCount();

}
