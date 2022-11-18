import { css, html, LitElement, nothing } from 'lit';
import { customElement, property } from 'lit/decorators.js';

@customElement('vaadin-session-debug-notifier')
export class SessionDebugNotifier extends LitElement {

    @property() outcome? : {
      success: boolean,
      message?: String,
      duration?: number,
    }

    static get styles() {
        return css`
      .notification {
        display: block;
        z-index: 99999999;
        position: absolute;
        max-width: calc(100% - 100px);
        min-height: 40px;
        top: 6px;
        right: 6px;
        padding: 14px 10px;
        border-radius: 4px;
        text-align: center;
        background: #f5e087e6;
        color: #42302B;
        box-sizing: border-box;
        box-shadow: inset 0 0 5px;
        cursor: move;
      }
      a {
        color: #0000ff96;
        cursor: pointer;
        font-weight: bold;
      }
      `;
    }

    render() {
        if (this.outcome && !this.outcome.success) {
        return html`
        <div class="notification">
            <div>${this.outcome.message}</div>
            <div><a @click=${this.onClose}>Close</a></div>
      </div>
    `;
        } else {
            return html`${nothing}`;
        }
    }

    onClose() {
        this.outcome = undefined;
    }

}