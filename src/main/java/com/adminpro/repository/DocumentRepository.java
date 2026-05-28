package com.adminpro.repository;

import com.adminpro.model.Document;
import com.adminpro.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByFolderOrderByNameAsc(Folder folder);
    List<Document> findByFolderIsNullOrderByNameAsc();
}
