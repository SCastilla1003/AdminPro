package com.adminpro.repository;

import com.adminpro.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByParentOrderByNameAsc(Folder parent);
    List<Folder> findByParentIsNullOrderByNameAsc();
}
