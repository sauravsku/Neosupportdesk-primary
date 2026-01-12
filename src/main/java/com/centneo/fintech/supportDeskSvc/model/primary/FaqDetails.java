package com.centneo.fintech.supportDeskSvc.model.primary;

import com.centneo.fintech.supportDeskSvc.util.StringListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import com.centneo.fintech.supportDeskSvc.model.BaseEntity;

@Entity
@Getter
@Setter
@Table(name = "faqs")
public class FaqDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String question;

    @Lob
    @Column(nullable = false)
    private String answer;

    @Column(name = "category")
    @Convert(converter = StringListConverter.class)
    private List<String> categories;

    @Column(name = "related_link")
    @Convert(converter = StringListConverter.class)
    private List<String> related;
}
