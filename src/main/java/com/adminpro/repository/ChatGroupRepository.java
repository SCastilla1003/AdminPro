package com.adminpro.repository;

import com.adminpro.model.ChatGroup;
import com.adminpro.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {

    @Query("SELECT g FROM ChatGroup g JOIN g.members m WHERE m = :user")
    List<ChatGroup> findByMember(@Param("user") User user);
}
