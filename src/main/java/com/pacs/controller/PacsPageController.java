package com.pacs.controller;

import com.pacs.service.Servicepacs;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class PacsPageController {

    @Autowired
    private Servicepacs servicepacs;

    @GetMapping("/")
    public String processPdfs() {
        servicepacs.processDirectory(
                "C:\\Users\\Hp\\Desktop\\pdfs",
                "C:\\Users\\Hp\\Desktop\\output_pdfs"
        );
        return "Traitement terminé !";
    }

}