package com.adminpro.controller;

import com.adminpro.model.Product;
import com.adminpro.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Controller
@RequestMapping("/inventario")
@RequiredArgsConstructor
public class InventoryController {

    private final ProductRepository productRepository;
    private final com.adminpro.service.ActivityLogService activityLog;

    @GetMapping
    public String index(Model model) {
        List<Product> products = productRepository.findAll();
        long lowStockCount = 0;
        for (Product p : products) {
            if (p.getStock() != null && p.getMinStock() != null && p.getStock() <= p.getMinStock()) {
                lowStockCount++;
            }
        }
        
        model.addAttribute("pageTitle", "Inventario y Stock");
        model.addAttribute("pageSubtitle", "Control de productos y existencias");
        model.addAttribute("activePage", "inventario");
        model.addAttribute("products", products);
        model.addAttribute("lowStockCount", lowStockCount);
        return "inventario/index";
    }

    @PostMapping("/guardar")
    public String saveProduct(@ModelAttribute Product product, RedirectAttributes ra) {
        boolean isNew = (product.getId() == null);
        productRepository.save(product);
        
        if (isNew) {
            activityLog.log("INVENTORY", "CREATE", "Producto '" + product.getName() + "' agregado al inventario");
        } else {
            activityLog.log("INVENTORY", "UPDATE", "Producto '" + product.getName() + "' actualizado");
        }
        
        ra.addFlashAttribute("successMsg", "Producto guardado correctamente.");
        return "redirect:/inventario";
    }

    @PostMapping("/eliminar/{id}")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes ra) {
        productRepository.findById(id).ifPresent(p -> {
            activityLog.log("INVENTORY", "DELETE", "Producto '" + p.getName() + "' eliminado del inventario");
        });
        productRepository.deleteById(id);
        ra.addFlashAttribute("successMsg", "Producto eliminado.");
        return "redirect:/inventario";
    }

    @GetMapping("/exportar")
    public void exportToPDF(HttpServletResponse response) throws java.io.IOException {
        List<Product> products = productRepository.findAll();

        response.setContentType("application/pdf");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=Inventario_AdminPro.pdf";
        response.setHeader(headerKey, headerValue);

        com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4);
        com.lowagie.text.pdf.PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // Add Logo
        try {
            java.net.URL logoUrl = getClass().getResource("/static/images/logo.png");
            if (logoUrl != null) {
                com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(logoUrl);
                logo.scaleToFit(60, 60);
                logo.setAlignment(com.lowagie.text.Image.ALIGN_CENTER);
                document.add(logo);
            }
        } catch (Exception e) {
            System.err.println("Could not load logo: " + e.getMessage());
        }
        
        com.lowagie.text.Paragraph companyName = new com.lowagie.text.Paragraph("ADMINPRO S.A.S", com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 12, new java.awt.Color(100, 100, 100)));
        companyName.setAlignment(com.lowagie.text.Paragraph.ALIGN_CENTER);
        document.add(companyName);

        com.lowagie.text.Font fontTitle = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD);
        fontTitle.setSize(18);
        fontTitle.setColor(0, 51, 153);

        com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("Reporte de Inventario y Stock", fontTitle);
        title.setAlignment(com.lowagie.text.Paragraph.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        
        String[] headers = {"Nombre", "Categoría", "Precio", "Stock", "Estado"};
        for (String header : headers) {
            com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(header, com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD)));
            cell.setPadding(8);
            cell.setBackgroundColor(new java.awt.Color(241, 245, 249));
            table.addCell(cell);
        }

        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(new java.util.Locale("es", "CO"));
        symbols.setGroupingSeparator('.');
        java.text.DecimalFormat df = new java.text.DecimalFormat("$#,###", symbols);

        for (Product p : products) {
            table.addCell(p.getName());
            table.addCell(p.getCategory());
            table.addCell(p.getPrice() != null ? df.format(p.getPrice()) : "$0");
            table.addCell(String.valueOf(p.getStock()));
            table.addCell(p.getStock() <= (p.getMinStock() != null ? p.getMinStock() : 0) ? "STOCK BAJO" : "OK");
        }

        document.add(table);
        document.close();
    }
}
