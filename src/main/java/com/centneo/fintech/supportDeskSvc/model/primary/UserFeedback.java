package com.centneo.fintech.supportDeskSvc.model.primary;


import com.centneo.fintech.supportDeskSvc.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "user_feedback")
public class UserFeedback extends BaseEntity {


    @Id
    @Column(name = "id", nullable = false, unique = true)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ssoId")
    private String ssoId;

    @Column(name = "feedback")
    private String feedback;

}
