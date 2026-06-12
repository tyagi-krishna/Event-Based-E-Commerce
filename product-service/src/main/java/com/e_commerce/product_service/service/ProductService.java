package com.e_commerce.product_service.service;

import com.e_commerce.product_service.dto.CreateProductRequest;
import com.e_commerce.product_service.dto.ProductResponse;
import com.e_commerce.product_service.dto.UpdateProductRequest;
import com.e_commerce.product_service.entity.Product;
import com.e_commerce.product_service.exception.ProductNotFoundException;
import com.e_commerce.product_service.exception.SkuAlreadyExistsException;
import com.e_commerce.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySku(sku)) {
            throw new SkuAlreadyExistsException(sku);
        }

        Product product = new Product();
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setActive(request.active() == null || request.active());

        Product savedProduct = productRepository.save(product);

        return ProductResponse.from(savedProduct);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        return productRepository.findAll()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return ProductResponse.from(findProduct(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(normalizeSku(sku))
                .orElseThrow(() -> new ProductNotFoundException(sku));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findProduct(id);

        if (request.sku() != null && !request.sku().isBlank()) {
            String sku = normalizeSku(request.sku());
            if (!sku.equals(product.getSku()) && productRepository.existsBySku(sku)) {
                throw new SkuAlreadyExistsException(sku);
            }
            product.setSku(sku);
        }

        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name().trim());
        }

        if (request.description() != null) {
            product.setDescription(trimToNull(request.description()));
        }

        if (request.price() != null) {
            product.setPrice(request.price());
        }

        if (request.stockQuantity() != null) {
            product.setStockQuantity(request.stockQuantity());
        }

        if (request.active() != null) {
            product.setActive(request.active());
        }

        product.setUpdatedAt(LocalDateTime.now());
        Product savedProduct = productRepository.save(product);

        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        productRepository.delete(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
