// Spring Data repository for contact
package com.docshelf.contact;

import com.docshelf.contact.entity.Contact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    List<Contact> findAllByOrderByFullNameAsc();
}
