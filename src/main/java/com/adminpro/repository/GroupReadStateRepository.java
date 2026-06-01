package com.adminpro.repository;

import com.adminpro.model.ChatGroup;
import com.adminpro.model.GroupReadState;
import com.adminpro.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupReadStateRepository extends JpaRepository<GroupReadState, Long> {

    Optional<GroupReadState> findByGroupAndUser(ChatGroup group, User user);
}
