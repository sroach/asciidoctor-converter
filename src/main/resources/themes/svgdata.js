const docopsData = {
    toggle(btn) {
        const card = btn.closest('.docops-media-card');
        const panel = card.querySelector('.docops-data-panel');
        if (panel.style.display === 'none') {
            this.renderCsvTable(card, panel);
            panel.style.display = 'block';
        } else {
            panel.style.display = 'none';
        }
    },
    renderCsvTable(card, panel) {
        const container = panel.querySelector('.docops-data-table-container');
        const header = panel.querySelector('.docops-data-header');
        if (container.innerHTML !== '') return;

        const svg = card.querySelector('svg');
        const metadata = svg.querySelector('metadata[type="text/csv"]');

        if (!metadata || !metadata.textContent.trim()) {
            container.innerHTML = '<div style="color:#666; padding: 10px; font-family: var(--font-mono); font-size: 0.7rem;">No embedded CSV metadata found in diagram.</div>';
            return;
        }

        try {
            // Parse the JSON data structure embedded by DocOps
            const csvData = JSON.parse(metadata.textContent.trim());

            // Add Export Actions to Header
            header.innerHTML = `
                <span>Embedded Data</span>
                <div class="docops-data-actions">
                    <button class="docops-action-btn" onclick="docopsData.exportTab('\${card.dataset.url}', this)">Copy TAB</button>
                    <button class="docops-action-btn" onclick="docopsData.exportExcel('\${card.dataset.url}', this)">Excel</button>
                    <button class="docops-btn-close" style="background:none; border:none; color:#fff; cursor:pointer; font-size:1.2rem;" onclick="this.closest('.docops-data-panel').style.display='none'">×</button>
                </div>
            `;

            if (!csvData.headers || !csvData.rows) {
                throw new Error("Invalid format");
            }

            let html = '<table class="docops-data-table"><thead><tr>';
            // Render Headers
            csvData.headers.forEach(h => {
                html += `<th>${h}</th>`;
            });
            html += '</tr></thead><tbody>';

            // Render Rows
            // Render Rows with Staggered Animation
            csvData.rows.forEach((row, index) => {
                const delay = (index * 0.05).toFixed(2);
                html += `<tr style="animation-delay: ${delay}s">`;
                row.forEach(cell => {
                    html += `<td>${cell}</td>`;
                });
                html += '</tr>';
            });

            html += '</tbody></table>';
            container.innerHTML = html;
        } catch (e) {
            console.error("Data parsing error:", e);
            container.innerHTML = '<div style="color:#ef4444; padding: 10px; font-family: var(--font-mono); font-size: 0.7rem;">Error parsing embedded data structure.</div>';
        }
    },
    exportTab(url, btn) {
        const card = btn.closest('.docops-media-card');
        const metadata = card.querySelector('metadata[type="text/csv"]');
        const data = JSON.parse(metadata.textContent.trim());

        const lines = [data.headers.join('\t')];
        data.rows.forEach(row => lines.push(row.join('\t')));

        navigator.clipboard.writeText(lines.join('\n')).then(() => {
            const old = btn.innerText;
            btn.innerText = 'COPIED!';
            setTimeout(() => btn.innerText = old, 2000);
        });
    },

    exportExcel(url, btn) {
        const card = btn.closest('.docops-media-card');
        const metadata = card.querySelector('metadata[type="text/csv"]');
        const data = JSON.parse(metadata.textContent.trim());

        const escapeXml = (s) => String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");

        const h = data.headers.map(val => `<Cell ss:StyleID="Header"><Data ss:Type="String">${escapeXml(val)}</Data></Cell>`).join("");
        const body = data.rows.map(r =>
                `<Row>` + r.map(v => {
                    const isNum = typeof v === "number" || (!isNaN(v) && v !== "" && !isNaN(parseFloat(v)));
                    return `<Cell><Data ss:Type="${isNum ? 'Number' : 'String'}">${isNum ? v : escapeXml(v)}</Data></Cell>`;
                }).join("") + `</Row>`
        ).join("");

        const xml = "\uFEFF" + `<?xml version="1.0"?><?mso-application progid="Excel.Sheet"?>
                                <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
                                  <Styles><Style ss:ID="Header"><Font ss:Bold="1"/></Style></Styles>
                                  <Worksheet ss:Name="DocOpsData"><Table><Row>${h}</Row>${body}</Table></Worksheet>
                                </Workbook>`;

        const blob = new Blob([xml], { type: "application/vnd.ms-excel;charset=UTF-8" });
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = "diagram_data.xls";
        link.click();
    }

};